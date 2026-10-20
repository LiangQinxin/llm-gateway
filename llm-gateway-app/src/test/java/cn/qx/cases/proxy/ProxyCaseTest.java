package cn.qx.cases.proxy;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.proxy.model.valobj.ProxyResult;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * ProxyCase 真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ProxyCaseTest {

    /** 上游基础 URL（含 /v1） */
    private static final String BASE_URL = "http://127.0.0.1:8777/v1";
    /** 上游 API 密钥 */
    private static final String API_KEY = "sk-waliapi-04dc74ce989d458e848a2c1616bf5611";
    /** 上游模型名 */
    private static final String MODEL = "deepseek-flash";

    /** 代理转发用例 */
    @Resource
    private ProxyCase proxyCase;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    @Test
    public void prepare_sanitizes_messages_and_detects_stream() {
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        JSONArray messages = new JSONArray();
        messages.add(message("assistant", ""));
        JSONObject toolMessage = message("assistant", "");
        toolMessage.put("tool_calls", JSON.parseArray("""
                [{"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{}"}}]
                """));
        messages.add(toolMessage);
        messages.add(message("user", "你好"));
        body.put("messages", messages);

        ProxyRequest streamRequest = proxyCase.prepare(body.toJSONString(), "application/json");
        log.info("预处理无 stream 字段: {}", JSON.toJSONString(streamRequest));
        assertFalse("Accept=json 时非流式", streamRequest.isStream());
        assertEquals("空 assistant 且无 tool_calls 应删除", 2,
                streamRequest.getBody().getJSONArray("messages").size());
        assertNotNull("有 tool_calls 的空 assistant 应保留并置 null content",
                streamRequest.getBody().getJSONArray("messages").getJSONObject(0)
                        .getJSONArray("tool_calls"));
        assertTrue("有 tool_calls 的空 assistant content 应为 null",
                streamRequest.getBody().getJSONArray("messages").getJSONObject(0).containsKey("content")
                        && streamRequest.getBody().getJSONArray("messages").getJSONObject(0).get("content") == null);

        ProxyRequest acceptStream = proxyCase.prepare(body.toJSONString(), "text/event-stream");
        assertTrue("body 缺省 stream 时 Accept 兜底为流式", acceptStream.isStream());

        body.put("stream", true);
        ProxyRequest bodyAuthoritative = proxyCase.prepare(body.toJSONString(), "application/json");
        assertTrue("body.stream=true 为唯一权威，不受 Accept 覆盖", bodyAuthoritative.isStream());

        JSONObject missing = new JSONObject();
        missing.put("messages", messages);
        assertThrows("缺 model 应 400", AppException.class,
                () -> proxyCase.prepare(missing.toJSONString(), "application/json"));
    }

    @Test
    public void forward_unary_not_found_and_stream() throws Exception {
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-proxy-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
        try {
            ProxyResult unary = proxyCase.forward(proxyCase.prepare(
                    requestJson(MODEL, false), "application/json"));
            log.info("非流式 ProxyResult: status={}, body={}", unary.getStatusCode(), unary.getRaw());
            assertEquals("非流式应 200", 200, unary.getStatusCode());
            assertTrue("非流式应 application/json", unary.getContentType().startsWith("application/json"));
            assertNotNull("应保留原始响应文本", unary.getRaw());
            assertTrue("原始响应应含 choices", unary.getRaw().contains("\"choices\""));

            ProxyResult notFound = proxyCase.forward(proxyCase.prepare(
                    requestJson("missing-" + UUID.randomUUID(), false), "application/json"));
            log.info("未命中 ProxyResult: status={}, body={}", notFound.getStatusCode(), notFound.getRaw());
            assertEquals("未命中应 404", 404, notFound.getStatusCode());
            assertTrue("未命中应 OpenAI 风格错误", notFound.getRaw().contains("model_not_found"));

            ProxyResult streaming = proxyCase.forward(proxyCase.prepare(
                    requestJson(MODEL, true), "*/*"));
            log.info("流式 ProxyResult: status={}, cancel={}",
                    streaming.getStatusCode(), streaming.getCancel());
            assertEquals("流式应 200", 200, streaming.getStatusCode());
            assertTrue("流式应 text/event-stream", streaming.getContentType().startsWith("text/event-stream"));
            assertNotNull("流式应带 cancel", streaming.getCancel());
            String sse = new String(streaming.getStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue("应含 data: 帧", sse.contains("data:"));
            assertTrue("应含 [DONE]", sse.contains("[DONE]"));
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    @Test
    public void upstream_non_2xx_error_body_is_passed_through() {
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-proxy-error-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"unsupported-" + UUID.randomUUID() + "\"]")
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
        try {
            String model = channel.activeModels().get(0);
            ProxyResult result = proxyCase.forward(proxyCase.prepare(
                    requestJson(model, false), "application/json"));
            log.info("上游错误透传: status={}, body={}", result.getStatusCode(), result.getRaw());
            assertTrue("上游非 2xx 应保留状态码", result.getStatusCode() >= 400);
            assertTrue("错误体应按 JSON 透传", result.getContentType().startsWith("application/json"));
            assertTrue("错误体应保留 upstream error", result.getRaw().contains("\"error\""));
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    private static JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String requestJson(String model, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", stream);
        JSONArray messages = new JSONArray();
        messages.add(message("user", "hello"));
        body.put("messages", messages);
        return body.toJSONString();
    }
}
