package cn.qx.infrastructure.adapter.port.llm.codec;

import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * SSE 协议转换器测试：保留事件、delta、响应与 usage 扩展字段。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SseProtocolTransformerTest {

    /**
     * Responses SSE → chat 转换保留扩展字段：vendor_trace、service_tier、usage.cache_hit 不得丢失。
     */
    @Test
    public void responses_sse_extensions_survive_conversion_to_chat() throws Exception {
        String sse = """
                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"hello","vendor_trace":"trace-2"}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp_1","model":"test-model","service_tier":"flex","usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2,"cache_hit":true}}}

                """;
        String transformed;
        try (InputStream stream = ProtocolCodecs.transformStream(
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)),
                ProtocolFamily.CC,
                ProtocolFamily.RP)) {
            transformed = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> frames = dataFrames(transformed);
        JSONObject delta = JSON.parseObject(frames.get(0));
        JSONObject completed = JSON.parseObject(frames.get(1));

        log.info("rp SSE 扩展字段转换: body={}", transformed);
        assertEquals("trace-2", delta.getString("vendor_trace"));
        assertEquals("flex", completed.getJSONObject("response").getString("service_tier"));
        assertEquals(true, completed.getJSONObject("usage").getBoolean("cache_hit"));
    }

    /** 提取 SSE 文本中所有 data: 负载（去掉空块与 [DONE]） */
    private static List<String> dataFrames(String body) {
        return Arrays.stream(body.split("\n\n"))
                .map(String::trim)
                .filter(block -> !block.isEmpty())
                .map(block -> Arrays.stream(block.split("\n"))
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).trim())
                        .reduce((first, second) -> first + "\n" + second)
                        .orElse(""))
                .filter(data -> !data.isEmpty() && !"[DONE]".equals(data))
                .toList();
    }
}
