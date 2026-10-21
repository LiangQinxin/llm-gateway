package cn.qx.domain.proxy.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

/**
 * SSE usage 旁路解析器：按行缓冲字节，只提取 usage，不干预原流转发。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SseUsageParserServiceImpl implements ISseUsageParserService {

    /** 当前未完成行 */
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    /** 最近一次解析到的总 Token 数 */
    private OptionalLong usageTokens = OptionalLong.empty();
    /** 跨帧累计的输入 Token 数 */
    private Long promptTokens;
    /** 跨帧累计的输出 Token 数 */
    private Long completionTokens;
    /** 跨帧累计的总 Token 数 */
    private Long totalTokens;

    /** 接受任意字节分块；跨块行在换行时拼接后解析。 */
    @Override
    public void accept(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        for (byte value : chunk) {
            if (value == '\n') {
                parseLine();
            } else {
                lineBuffer.write(value);
            }
        }
    }

    /** 结束输入，处理末尾未带换行的 data 行。 */
    @Override
    public void finish() {
        parseLine();
    }

    /** 已解析到的总 Token 数；无 usage 或解析失败时为空。 */
    @Override
    public OptionalLong usageTokens() {
        return usageTokens;
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
            JSONObject event = JSON.parseObject(payload);
            JSONObject usage = event.getJSONObject("usage");
            if (usage == null && event.getJSONObject("message") != null) {
                usage = event.getJSONObject("message").getJSONObject("usage");
            }
            if (usage == null && event.getJSONObject("response") != null) {
                usage = event.getJSONObject("response").getJSONObject("usage");
            }
            if (usage == null) {
                return;
            }
            Long total = usage.getLong("total_tokens");
            if (total != null) {
                totalTokens = mergeNonZero(totalTokens, total);
            }
            Long prompt = usage.getLong("prompt_tokens") != null
                    ? usage.getLong("prompt_tokens")
                    : usage.getLong("input_tokens");
            Long completion = usage.getLong("completion_tokens") != null
                    ? usage.getLong("completion_tokens")
                    : usage.getLong("output_tokens");
            promptTokens = mergeNonZero(promptTokens, prompt);
            completionTokens = mergeNonZero(completionTokens, completion);
            if (totalTokens != null) {
                usageTokens = OptionalLong.of(totalTokens);
            } else if (promptTokens != null && completionTokens != null) {
                usageTokens = OptionalLong.of(promptTokens + completionTokens);
            }
        } catch (Exception ignored) {
            // best-effort：格式漂移时静默跳过，绝不阻断透传。
        }
    }

    private static Long mergeNonZero(Long current, Long incoming) {
        if (incoming == null) {
            return current;
        }
        if (incoming == 0 && current != null) {
            return current;
        }
        return incoming;
    }
}
