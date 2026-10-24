package cn.qx.cases.proxy;

import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

    /** 上游模型名 */
    private static final String MODEL = "proxy-case-" + UUID.randomUUID();

    /** 代理转发用例 */
    @Resource
    private ProxyCase proxyCase;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    /**
     * 请求预处理：消息清洗与流式判定。
     * <pre>
     * 空 assistant 且无 tool_calls → 删除；有 tool_calls → content 置 null；
     * stream 判定优先级：body.stream 唯一权威 &gt; Accept 兜底；缺 model → 400
     * </pre>
     */
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

    /**
     * 数据面转发三态：非流式 200、模型未命中 404、流式 SSE 到 [DONE]。
     */
    @Test
    public void forward_unary_not_found_and_stream() throws Exception {
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("测试-proxy-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-unary","choices":[{"index":0,
                             "message":{"role":"assistant","content":"ok"},
                             "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                            """));
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"id":"chunk-1","choices":[{"index":0,"delta":{"content":"ok"}}]}

                            data: [DONE]

                            """));
            upstream.start();
            Channel channel = channelCase.create(Channel.builder()
                    .name("测试-proxy-" + UUID.randomUUID())
                    .type("deepseek")
                    .baseUrl(upstream.url("/v1").toString())
                    .apiKey("upstream-key")
                    .models(JSON.toJSONString(new String[]{MODEL}))
                    .priority(10000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            try {
                ProxyResult unary = proxyCase.forward(proxyCase.prepare(
                        requestJson(MODEL, false), "application/json"), apiKey.getKey());
                log.info("非流式 ProxyResult: status={}, body={}", unary.getStatusCode(), unary.getRaw());
                assertEquals("非流式应 200", 200, unary.getStatusCode());
                assertTrue("非流式应 application/json", unary.getContentType().startsWith("application/json"));
                assertNotNull("应保留原始响应文本", unary.getRaw());
                assertTrue("原始响应应含 choices", unary.getRaw().contains("\"choices\""));

                ProxyResult notFound = proxyCase.forward(proxyCase.prepare(
                        requestJson("missing-" + UUID.randomUUID(), false), "application/json"),
                        apiKey.getKey());
                log.info("未命中 ProxyResult: status={}, body={}",
                        notFound.getStatusCode(), notFound.getRaw());
                assertEquals("未命中应 404", 404, notFound.getStatusCode());
                assertTrue("未命中应 OpenAI 风格错误", notFound.getRaw().contains("model_not_found"));

                ProxyResult streaming = proxyCase.forward(proxyCase.prepare(
                        requestJson(MODEL, true), "*/*"), apiKey.getKey());
                log.info("流式 ProxyResult: status={}, cancel={}",
                        streaming.getStatusCode(), streaming.getCancel());
                assertEquals("流式应 200", 200, streaming.getStatusCode());
                assertTrue("流式应 text/event-stream",
                        streaming.getContentType().startsWith("text/event-stream"));
                assertNotNull("流式应带 cancel", streaming.getCancel());
                String sse = new String(streaming.getStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue("应含 data: 帧", sse.contains("data:"));
                assertTrue("应含 [DONE]", sse.contains("[DONE]"));
            } finally {
                channelCase.delete(channel.getId());
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 上游非 2xx 错误体透传：状态码保留、错误体按 JSON 原样回传。
     */
    @Test
    public void upstream_non_2xx_error_body_is_passed_through() throws Exception {
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("测试-proxy-error-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"upstream failed\"}}"));
            upstream.start();
            Channel channel = channelCase.create(Channel.builder()
                    .name("测试-proxy-error-" + UUID.randomUUID())
                    .type("deepseek")
                    .baseUrl(upstream.url("/v1").toString())
                    .apiKey("upstream-key")
                    .models("[\"unsupported-" + UUID.randomUUID() + "\"]")
                    .priority(10000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            try {
                String model = channel.activeModels().get(0);
                ProxyResult result = proxyCase.forward(proxyCase.prepare(
                        requestJson(model, false), "application/json"), apiKey.getKey());
                log.info("上游错误透传: status={}, body={}", result.getStatusCode(), result.getRaw());
                assertEquals("最后一次 HTTP 状态应透传", 500, result.getStatusCode());
                assertTrue("错误体应按 JSON 透传",
                        result.getContentType().startsWith("application/json"));
                assertTrue("错误体应保留 upstream error", result.getRaw().contains("\"error\""));
            } finally {
                channelCase.delete(channel.getId());
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /** 构造一条 chat 消息 */
    private static JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /** 构造 chat/completions 请求体 JSON */
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
