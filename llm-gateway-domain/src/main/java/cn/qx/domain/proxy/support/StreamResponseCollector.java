package cn.qx.domain.proxy.support;

import cn.qx.domain.channel.model.valobj.TokenUsage;
import cn.qx.domain.proxy.model.valobj.StreamAuditSnapshot;
import cn.qx.domain.proxy.service.ISseUsageParserService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流式响应旁路审计：原样透传的同时累积 usage、内容和工具调用，终止时重建 choices。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StreamResponseCollector {

    /** 审计重建缓冲上限（4MB） */
    private static final int MAX_AUDIT_BYTES = 4 * 1024 * 1024;

    /** SSE usage 解析器 */
    private final ISseUsageParserService usageParser;
    /** 未完成 SSE 行 */
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    /** 按 choice index 累积消息内容 */
    private final Map<Integer, ChoiceAccumulator> choices = new LinkedHashMap<>();

    /** 已接收字节数 */
    private long receivedBytes;
    /** 是否超过审计缓冲上限 */
    private boolean truncated;
    /** 是否已结束，防止重复解析 */
    private boolean finished;

    public StreamResponseCollector(ISseUsageParserService usageParser) {
        this.usageParser = usageParser;
    }

    /** 接受任意字节分块，不改变原流转发。 */
    public synchronized void accept(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        receivedBytes += chunk.length;
        if (receivedBytes > MAX_AUDIT_BYTES) {
            truncated = true;
        }
        usageParser.accept(chunk);
        long consumedBefore = receivedBytes - chunk.length;
        int parseLength = consumedBefore >= MAX_AUDIT_BYTES
                ? 0
                : (int) Math.min(chunk.length, MAX_AUDIT_BYTES - consumedBefore);
        for (int i = 0; i < parseLength; i++) {
            byte value = chunk[i];
            if (value == '\n') {
                parseLine();
            } else {
                lineBuffer.write(value);
            }
        }
    }

    /** 结束输入并返回最终快照；可重复调用。 */
    public synchronized StreamAuditSnapshot finish() {
        if (!finished) {
            parseLine();
            usageParser.finish();
            finished = true;
        }
        return snapshot();
    }

    /** 返回当前累积快照，不结束输入。 */
    public synchronized StreamAuditSnapshot snapshot() {
        JSONArray choicesJson = new JSONArray();
        for (Map.Entry<Integer, ChoiceAccumulator> entry : choices.entrySet()) {
            choicesJson.add(entry.getValue().toChoice(entry.getKey()));
        }
        if (truncated) {
            JSONObject marker = new JSONObject();
            marker.put("truncated", true);
            choicesJson.add(marker);
        }
        TokenUsage usage = usageParser.usage().orElse(null);
        return StreamAuditSnapshot.builder()
                .responseChoices(choicesJson.toJSONString())
                .usage(usage)
                .truncated(truncated)
                .build();
    }

    private void parseLine() {
        if (lineBuffer.size() == 0) {
            return;
        }
        String line = lineBuffer.toString(StandardCharsets.UTF_8);
        lineBuffer.reset();
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        line = line.trim();
        if (!line.startsWith("data:")) {
            return;
        }
        String payload = line.substring("data:".length()).trim();
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return;
        }
        try {
            parseEvent(JSON.parseObject(payload));
        } catch (Exception ignored) {
            // best-effort：格式漂移绝不阻断原流透传。
        }
    }

    private void parseEvent(JSONObject event) {
        JSONArray deltaChoices = event.getJSONArray("choices");
        if (deltaChoices != null) {
            for (int i = 0; i < deltaChoices.size(); i++) {
                JSONObject choice = deltaChoices.getJSONObject(i);
                int index = choice.getIntValue("index", i);
                ChoiceAccumulator accumulator = choices.computeIfAbsent(
                        index, ignored -> new ChoiceAccumulator());
                accumulator.accept(choice);
            }
        }

        // Anthropic Messages 原生流
        String type = event.getString("type");
        if ("content_block_delta".equals(type)) {
            ChoiceAccumulator accumulator = choices.computeIfAbsent(
                    event.getIntValue("index", 0), ignored -> new ChoiceAccumulator());
            JSONObject delta = event.getJSONObject("delta");
            if (delta != null) {
                accumulator.content.append(delta.getString("text") == null
                        ? "" : delta.getString("text"));
                accumulator.reasoning.append(delta.getString("thinking") == null
                        ? "" : delta.getString("thinking"));
            }
        } else if ("message_delta".equals(type)) {
            JSONObject delta = event.getJSONObject("delta");
            if (delta != null && delta.getString("stop_reason") != null) {
                choices.computeIfAbsent(0, ignored -> new ChoiceAccumulator())
                        .finishReason = delta.getString("stop_reason");
            }
        }

        // OpenAI Responses 原生流
        if ("response.output_text.delta".equals(type)) {
            choices.computeIfAbsent(0, ignored -> new ChoiceAccumulator())
                    .content.append(event.getString("delta") == null
                            ? "" : event.getString("delta"));
        }
    }

    /** 单个 choice 的消息累积器。 */
    private static final class ChoiceAccumulator {

        private String role = "assistant";
        private final StringBuilder content = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, JSONObject> toolCalls = new LinkedHashMap<>();
        private String finishReason;

        private void accept(JSONObject choice) {
            finishReason = choice.getString("finish_reason") == null
                    ? finishReason : choice.getString("finish_reason");
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null) {
                return;
            }
            if (delta.getString("role") != null) {
                role = delta.getString("role");
            }
            if (delta.getString("content") != null) {
                content.append(delta.getString("content"));
            }
            String reasoningContent = delta.getString("reasoning_content");
            if (reasoningContent != null) {
                reasoning.append(reasoningContent);
            }
            JSONArray deltas = delta.getJSONArray("tool_calls");
            if (deltas != null) {
                mergeToolCalls(deltas);
            }
        }

        private void mergeToolCalls(JSONArray deltas) {
            for (int i = 0; i < deltas.size(); i++) {
                JSONObject delta = deltas.getJSONObject(i);
                int index = delta.getIntValue("index", i);
                JSONObject tool = toolCalls.computeIfAbsent(index, ignored -> {
                    JSONObject created = new JSONObject();
                    created.put("type", "function");
                    created.put("function", new JSONObject());
                    return created;
                });
                copyIfPresent(delta, tool, "id");
                copyIfPresent(delta, tool, "type");
                JSONObject function = delta.getJSONObject("function");
                if (function != null) {
                    JSONObject target = tool.getJSONObject("function");
                    copyIfPresent(function, target, "name");
                    String arguments = function.getString("arguments");
                    if (arguments != null) {
                        target.put("arguments",
                                target.getString("arguments") == null
                                        ? arguments
                                        : target.getString("arguments") + arguments);
                    }
                }
            }
        }

        private JSONObject toChoice(int index) {
            JSONObject message = new JSONObject();
            message.put("role", role);
            message.put("content", content.length() == 0 ? null : content.toString());
            if (reasoning.length() > 0) {
                message.put("reasoning_content", reasoning.toString());
            }
            if (!toolCalls.isEmpty()) {
                JSONArray tools = new JSONArray();
                tools.addAll(toolCalls.values());
                message.put("tool_calls", tools);
            }
            JSONObject choice = new JSONObject();
            choice.put("index", index);
            choice.put("message", message);
            if (finishReason != null) {
                choice.put("finish_reason", finishReason);
            }
            return choice;
        }

        private static void copyIfPresent(JSONObject source, JSONObject target, String key) {
            String value = source.getString(key);
            if (value != null && !value.isBlank()) {
                target.put(key, value);
            }
        }
    }
}
