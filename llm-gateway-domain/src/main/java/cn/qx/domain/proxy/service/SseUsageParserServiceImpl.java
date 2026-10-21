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
            JSONObject usage = JSON.parseObject(payload).getJSONObject("usage");
            if (usage == null) {
                return;
            }
            Long total = usage.getLong("total_tokens");
            if (total != null) {
                usageTokens = OptionalLong.of(total);
                return;
            }
            Long prompt = usage.getLong("prompt_tokens");
            Long completion = usage.getLong("completion_tokens");
            if (prompt != null && completion != null) {
                usageTokens = OptionalLong.of(prompt + completion);
            }
        } catch (Exception ignored) {
            // best-effort：格式漂移时静默跳过，绝不阻断透传。
        }
    }
}
