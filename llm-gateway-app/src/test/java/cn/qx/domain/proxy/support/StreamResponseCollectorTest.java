package cn.qx.domain.proxy.support;

import cn.qx.domain.proxy.model.valobj.StreamAuditSnapshot;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * StreamResponseCollector 纯领域测试。
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class StreamResponseCollectorTest {

    @Resource
    private ObjectProvider<StreamResponseCollector> collectorProvider;

    /**
     * reasoning_content 与 tool_calls.function.arguments 均按增量拼接进同构消息。
     */
    @Test
    public void reconstructs_reasoning_and_tool_call_arguments() {
        StreamResponseCollector collector = collectorProvider.getObject();
        collector.accept(frame(
                "{\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\","
                        + "\"reasoning_content\":\"think \",\"tool_calls\":[{\"index\":0,"
                        + "\"id\":\"call-1\",\"type\":\"function\","
                        + "\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"q\\\":\"}}]}}]}"));
        collector.accept(frame(
                "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"\\\"x\\\"}\"}}]},"
                        + "\"finish_reason\":\"tool_calls\"}]}"));
        StreamAuditSnapshot snapshot = collector.finish();

        JSONArray choices = JSON.parseArray(snapshot.getResponseChoices());
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        assertEquals("think ", message.getString("reasoning_content"));
        JSONObject function = message.getJSONArray("tool_calls")
                .getJSONObject(0).getJSONObject("function");
        assertEquals("lookup", function.getString("name"));
        assertEquals("{\"q\":\"x\"}", function.getString("arguments"));
        assertFalse(snapshot.isTruncated());
    }

    /**
     * 超过 4MB 后只标记截断，不再继续累积正文。
     */
    @Test
    public void marks_truncation_after_four_megabytes() {
        StreamResponseCollector collector = collectorProvider.getObject();
        String prefix = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"";
        int total = 4 * 1024 * 1024 + 1024;
        String overflow = prefix + "a".repeat(total) + "\"}}]}\n\n";

        collector.accept(overflow.getBytes(StandardCharsets.UTF_8));
        StreamAuditSnapshot snapshot = collector.finish();

        assertTrue(snapshot.isTruncated());
        assertTrue(snapshot.getResponseChoices().contains("\"truncated\":true"));
        assertTrue("超过上限后不应把整段正文塞进 response_choices",
                snapshot.getResponseChoices().length() < 1024);
    }

    private static byte[] frame(String json) {
        return ("data: " + json.trim() + "\n\n").getBytes(StandardCharsets.UTF_8);
    }
}
