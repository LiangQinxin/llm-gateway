package cn.qx.trigger.gateway;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.identity.model.entity.ApiKey;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 入站三协议族真实容器测试：HTTP 入口 + MockWebServer 上游 + 本地 MySQL。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InboundProtocolTest {

    /** 随机应用端口 */
    @LocalServerPort
    private int port;

    /** 假上游 */
    private MockWebServer upstream;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    /** HTTP 客户端 */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Before
    public void setUp() throws IOException {
        upstream = new MockWebServer();
        upstream.start();
    }

    @After
    public void tearDown() throws IOException {
        upstream.shutdown();
    }

    /**
     * Anthropic 原生 /v1/messages 字节保真透传：x-api-key、anthropic-version 头透传，请求响应原样往返。
     */
    @Test
    public void anthropic_native_messages_passthrough() throws Exception {
        String model = "claude-test-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel("anthropic", model, upstream.url("").toString(), "{}");
        String responseBody = """
                {"id":"msg_test","type":"message","role":"assistant","model":"%s","content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn","usage":{"input_tokens":3,"output_tokens":2}}
                """.formatted(model);
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseBody));

            JSONObject body = messagesBody(model);
            String requestJson = body.toJSONString();
            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("Anthropic 原生透传: status={}, body={}, upstream={}",
                    response.statusCode(), response.body(), forwarded);
            assertEquals(200, response.statusCode());
            assertEquals("Anthropic 响应应字节保真", responseBody, response.body());
            assertEquals("/v1/messages", forwarded.getPath());
            assertEquals("upstream-key", forwarded.getHeader("x-api-key"));
            assertEquals("2023-06-01", forwarded.getHeader("anthropic-version"));
            String forwardedRaw = forwarded.getBody().readUtf8();
            assertEquals("原生请求应字节保真", requestJson, forwardedRaw);
            JSONObject forwardedBody = JSON.parseObject(forwardedRaw);
            assertEquals(model, forwardedBody.getString("model"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Anthropic 原生流式 SSE 字节保真：不注入 stream_options、不改写空 assistant 消息，按 usage 记账。
     */
    @Test
    public void anthropic_native_stream_is_byte_exact() throws Exception {
        String model = "ms-native-stream-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        String sse = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_native","model":"%s","role":"assistant","content":[],"usage":{"input_tokens":2,"output_tokens":0}}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}

                event: message_stop
                data: {"type":"message_stop"}

                """.formatted(model);
        try {
            upstream.enqueue(sseResponse(sse));
            JSONObject body = messagesBody(model);
            JSONObject emptyAssistant = new JSONObject();
            emptyAssistant.put("role", "assistant");
            emptyAssistant.put("content", "");
            body.getJSONArray("messages").add(emptyAssistant);
            body.put("stream", true);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject forwardedBody = JSON.parseObject(forwarded.getBody().readUtf8());

            log.info("ms 原生 SSE 字节对比: status={}", response.statusCode());
            assertEquals(200, response.statusCode());
            assertEquals(sse, response.body());
            assertFalse("原生 ms 流不得注入 CC stream_options",
                    forwardedBody.containsKey("stream_options"));
            assertEquals("原生 ms 请求不得改写空 assistant 消息",
                    "", forwardedBody.getJSONArray("messages")
                            .getJSONObject(1).getString("content"));
            waitForQuota(apiKey.getId(), 3L);
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms → CC 渠道转换：max_tokens 兜底、system 提升为消息，响应转回 message 结构。
     */
    @Test
    public void messages_to_openai_channel_converts_request_and_response() throws Exception {
        String model = "ms-to-cc-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-1","model":"%s","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
                            """.formatted(model)));

            JSONObject body = messagesBody(model);
            body.remove("max_tokens");
            body.put("system", "be nice");
            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(forwarded.getBody().readUtf8());
            JSONObject converted = JSON.parseObject(response.body());

            log.info("ms→cc 响应: status={}, body={}, upstreamBody={}",
                    response.statusCode(), converted, upstreamBody);
            assertEquals(200, response.statusCode());
            assertEquals("/v1/chat/completions", forwarded.getPath());
            assertEquals("Bearer upstream-key", forwarded.getHeader("Authorization"));
            assertEquals(4096L, upstreamBody.getLongValue("max_tokens"));
            assertEquals("system", upstreamBody.getJSONArray("messages").getJSONObject(0).getString("role"));
            assertEquals("be nice", upstreamBody.getJSONArray("messages").getJSONObject(0).getString("content"));
            assertEquals("message", converted.getString("type"));
            assertEquals("hello", converted.getJSONArray("content").getJSONObject(0).getString("text"));
            assertEquals("end_turn", converted.getString("stop_reason"));
            assertEquals(3L, converted.getJSONObject("usage").getLongValue("input_tokens"));
            assertEquals(2L, converted.getJSONObject("usage").getLongValue("output_tokens"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * CC → ms 渠道转换：system/metadata 映射，响应转回 chat completions 结构。
     */
    @Test
    public void chat_to_anthropic_channel_converts_request_and_response() throws Exception {
        String model = "cc-to-ms-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"msg_1","type":"message","role":"assistant","model":"%s","content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn","usage":{"input_tokens":4,"output_tokens":2}}
                            """.formatted(model)));

            JSONObject body = chatBody(model);
            body.put("metadata", JSON.parseObject("{\"trace_id\":\"trace-1\"}"));
            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", body, null);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(forwarded.getBody().readUtf8());
            JSONObject converted = JSON.parseObject(response.body());

            log.info("cc→ms 响应: status={}, body={}, upstreamBody={}",
                    response.statusCode(), converted, upstreamBody);
            assertEquals(200, response.statusCode());
            assertEquals("/v1/messages", forwarded.getPath());
            assertEquals("upstream-key", forwarded.getHeader("x-api-key"));
            assertEquals("2023-06-01", forwarded.getHeader("anthropic-version"));
            assertEquals("be nice", upstreamBody.getString("system"));
            assertEquals(4096L, upstreamBody.getLongValue("max_tokens"));
            assertEquals("trace-1", upstreamBody.getJSONObject("metadata").getString("trace_id"));
            assertEquals("hello",
                    converted.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content"));
            assertEquals("stop",
                    converted.getJSONArray("choices").getJSONObject(0).getString("finish_reason"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses 原生透传到 openai 渠道：请求响应字节保真往返。
     */
    @Test
    public void responses_native_passthrough_to_openai_channel() throws Exception {
        String model = "rp-native-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        String responseBody = """
                {"id":"resp_1","object":"response","model":"%s","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"hello"}]}],"usage":{"input_tokens":2,"output_tokens":1,"total_tokens":3}}
                """.formatted(model);
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(responseBody));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("rp 原生透传: status={}, body={}, upstream={}",
                    response.statusCode(), response.body(), forwarded);
            assertEquals(200, response.statusCode());
            assertEquals(responseBody, response.body());
            assertEquals("/v1/responses", forwarded.getPath());
            assertEquals("Bearer upstream-key", forwarded.getHeader("Authorization"));
            assertEquals("rp 原生请求应字节保真",
                    responsesBody(model).toJSONString(), forwarded.getBody().readUtf8());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses 原生流式不得注入 CC stream_options。
     */
    @Test
    public void responses_native_stream_does_not_inject_chat_stream_options() throws Exception {
        String model = "rp-native-stream-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        String sse = """
                event: response.output_text.delta
                data: {"type":"response.output_text.delta","delta":"hello"}

                event: response.completed
                data: {"type":"response.completed","response":{"id":"resp_s","model":"%s","usage":{"input_tokens":2,"output_tokens":1,"total_tokens":3}}}

                """.formatted(model);
        try {
            upstream.enqueue(sseResponse(sse));
            JSONObject body = responsesBody(model);
            body.put("stream", true);

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", body, "text/event-stream");
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject forwardedBody = JSON.parseObject(forwarded.getBody().readUtf8());

            log.info("rp 原生 SSE: status={}, path={}, upstreamBody={}",
                    response.statusCode(), forwarded.getPath(), forwardedBody);
            assertEquals(200, response.statusCode());
            assertEquals(sse, response.body());
            assertEquals("/v1/responses", forwarded.getPath());
            assertFalse("原生 rp 流不得注入 CC stream_options",
                    forwardedBody.containsKey("stream_options"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses → google 渠道降级走 /v1/chat/completions，响应转回 response 结构。
     */
    @Test
    public void responses_to_google_falls_back_to_chat_completions() throws Exception {
        String model = "google-rp-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "google", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-google","model":"%s","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                            """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("google rp fallback: status={}, path={}, body={}",
                    response.statusCode(), forwarded.getPath(), response.body());
            assertEquals(200, response.statusCode());
            assertEquals("/v1/chat/completions", forwarded.getPath());
            assertEquals("response", JSON.parseObject(response.body()).getString("object"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses → ms 渠道经 canonical 转换：output/usage 字段映射。
     */
    @Test
    public void responses_to_anthropic_channel_converts_through_canonical() throws Exception {
        String model = "rp-to-ms-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"msg_rp","type":"message","role":"assistant","model":"%s","content":[{"type":"text","text":"hello"}],"stop_reason":"end_turn","usage":{"input_tokens":5,"output_tokens":2}}
                            """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(forwarded.getBody().readUtf8());
            JSONObject converted = JSON.parseObject(response.body());

            log.info("rp→ms 响应: status={}, body={}, upstreamBody={}",
                    response.statusCode(), converted, upstreamBody);
            assertEquals(200, response.statusCode());
            assertEquals("/v1/messages", forwarded.getPath());
            assertEquals("be nice", upstreamBody.getString("system"));
            assertEquals("hi", upstreamBody.getJSONArray("messages")
                    .getJSONObject(0).getString("content"));
            assertEquals("response", converted.getString("object"));
            assertEquals("hello", converted.getJSONArray("output").getJSONObject(0)
                    .getJSONArray("content").getJSONObject(0).getString("text"));
            assertEquals(5L, converted.getJSONObject("usage").getLongValue("input_tokens"));
            assertEquals(2L, converted.getJSONObject("usage").getLongValue("output_tokens"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms 流式 → CC 上游 SSE 事件序列转换：message_start/content_block_message_delta/message_stop
     */
    @Test
    public void messages_stream_to_openai_channel_converts_cc_sse() throws Exception {
        String model = "ms-stream-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(sseResponse("""
                    data: {"id":"chatcmpl-s","model":"%s","choices":[{"index":0,"delta":{"content":"hello"}}]}

                    data: {"id":"chatcmpl-s","model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"length"}]}

                    data: {"id":"chatcmpl-s","model":"%s","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}

                    data: [DONE]

                    """.formatted(model, model, model)));

            JSONObject body = messagesBody(model);
            body.put("stream", true);
            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(forwarded.getBody().readUtf8());

            log.info("ms 流式→cc 上游 SSE: status={}, body={}, upstreamBody={}",
                    response.statusCode(), response.body(), upstreamBody);
            assertEquals(200, response.statusCode());
            assertEquals(List.of("message_start", "content_block_start", "content_block_delta",
                    "message_delta", "message_stop"), sseEventNames(response.body()));
            assertTrue(response.body().contains("\"text\":\"hello\""));
            assertTrue(response.body().contains("\"stop_reason\":\"max_tokens\""));
            assertEquals(true, upstreamBody.getBoolean("stream"));
            assertEquals(true, upstreamBody.getJSONObject("stream_options").getBoolean("include_usage"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * CC 流式 → ms 上游 SSE：delta 扩展字段（signature/vendor_state/vendor_trace）保留，usage 合并。
     */
    @Test
    public void chat_stream_to_anthropic_channel_converts_ms_sse() throws Exception {
        String model = "cc-stream-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(sseResponse("""
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_s","model":"%s","role":"assistant","content":[],"usage":{"input_tokens":3,"output_tokens":0}}}

                    event: content_block_start
                    data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello","signature":"sig-1"},"vendor_trace":"trace-1"}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"max_tokens","vendor_state":"state-1"},"usage":{"output_tokens":2}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", streamChatBody(model), "text/event-stream");
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(forwarded.getBody().readUtf8());

            log.info("cc 流式→ms 上游 SSE: status={}, body={}, upstreamBody={}",
                    response.statusCode(), response.body(), upstreamBody);
            assertEquals(200, response.statusCode());
            List<String> dataFrames = sseDataFrames(response.body());
            assertEquals(3, dataFrames.size());
            JSONObject firstFrame = JSON.parseObject(dataFrames.get(0));
            JSONObject firstDelta = firstFrame.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("delta");
            assertEquals("hello", firstDelta.getString("content"));
            assertEquals("sig-1", firstDelta.getString("signature"));
            assertEquals("trace-1", firstFrame.getString("vendor_trace"));
            assertEquals(5L, JSON.parseObject(dataFrames.get(1))
                    .getJSONObject("usage").getLongValue("total_tokens"));
            assertEquals("length", JSON.parseObject(dataFrames.get(1))
                    .getJSONArray("choices").getJSONObject(0).getString("finish_reason"));
            assertEquals("state-1", JSON.parseObject(dataFrames.get(1))
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("delta").getString("vendor_state"));
            assertEquals("[DONE]", dataFrames.get(2));
            assertEquals(true, upstreamBody.getBoolean("stream"));
            assertEquals(4096L, upstreamBody.getLongValue("max_tokens"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * DeepSeek 的 /v1/messages 是原生端点：路径 /anthropic/v1/messages、x-api-key 透传。
     */
    @Test
    public void deepseek_ms_endpoint_is_native() throws Exception {
        String model = "deepseek-ms-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "deepseek", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"msg_ds","type":"message","role":"assistant","model":"%s","content":[{"type":"text","text":"native"}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}
                            """.formatted(model)));

            HttpResponse<String> response = postMessages(apiKey.getKey(), messagesBody(model));
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("DeepSeek ms 原生: status={}, body={}, path={}",
                    response.statusCode(), response.body(), forwarded.getPath());
            assertEquals(200, response.statusCode());
            assertEquals("/anthropic/v1/messages", forwarded.getPath());
            assertEquals("upstream-key", forwarded.getHeader("x-api-key"));
            assertEquals("native", JSON.parseObject(response.body())
                    .getJSONArray("content").getJSONObject(0).getString("text"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses 流式 → ms 上游转换：rp 事件名与 usage 合并，按 total_tokens 记账。
     */
    @Test
    public void responses_stream_to_anthropic_channel_converts_to_rp_events() throws Exception {
        String model = "rp-stream-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(sseResponse("""
                    event: message_start
                    data: {"type":"message_start","message":{"id":"msg_rps","model":"%s","role":"assistant","content":[]}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}

                    event: message_delta
                    data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"input_tokens":3,"output_tokens":2}}

                    event: message_stop
                    data: {"type":"message_stop"}

                    """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), "text/event-stream");

            log.info("rp 流式透传: status={}, body={}", response.statusCode(), response.body());
            assertEquals(200, response.statusCode());
            assertEquals(List.of("response.output_text.delta", "response.completed"),
                    sseEventNames(response.body()));
            List<String> dataFrames = sseDataFrames(response.body());
            assertEquals("hello", JSON.parseObject(dataFrames.get(0)).getString("delta"));
            JSONObject usage = JSON.parseObject(dataFrames.get(1))
                    .getJSONObject("response").getJSONObject("usage");
            assertEquals(3L, usage.getLongValue("input_tokens"));
            assertEquals(2L, usage.getLongValue("output_tokens"));
            assertEquals(5L, usage.getLongValue("total_tokens"));
            waitForQuota(apiKey.getId(), 5L);
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * Responses → DeepSeek 降级到主 CC 端点 /v1/chat/completions。
     */
    @Test
    public void responses_to_deepseek_falls_back_to_primary_cc_endpoint() throws Exception {
        String model = "rp-deepseek-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "deepseek", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-rp","model":"%s","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                            """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            RecordedRequest forwarded = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject converted = JSON.parseObject(response.body());

            log.info("rp 深寻 fallback: status={}, path={}, body={}",
                    response.statusCode(), forwarded.getPath(), converted);
            assertEquals(200, response.statusCode());
            assertEquals("/v1/chat/completions", forwarded.getPath());
            assertEquals("Bearer upstream-key", forwarded.getHeader("Authorization"));
            assertEquals("response", converted.getString("object"));
            assertEquals("hello", converted.getJSONArray("output").getJSONObject(0)
                    .getJSONArray("content").getJSONObject(0).getString("text"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms 无效密钥返回 Anthropic 错误形状（authentication_error）。
     */
    @Test
    public void messages_invalid_key_uses_anthropic_error_shape() throws Exception {
        HttpResponse<String> response = postMessages(
                "sk-invalid-" + UUID.randomUUID(), messagesBody("any-model"));
        JSONObject body = JSON.parseObject(response.body());

        log.info("ms 无效密钥错误体: status={}, body={}", response.statusCode(), body);
        assertEquals(401, response.statusCode());
        assertEquals("error", body.getString("type"));
        assertEquals("authentication_error", body.getJSONObject("error").getString("type"));
    }

    /**
     * ms 缺密钥在 filter 层拦截，返回 Anthropic 错误形状。
     */
    @Test
    public void messages_missing_key_is_rejected_by_filter_in_anthropic_shape() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/v1/messages"))
                .header("Content-Type", "application/json")
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        messagesBody("any-model").toJSONString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject body = JSON.parseObject(response.body());

        log.info("ms 缺密钥响应: status={}, body={}", response.statusCode(), body);
        assertEquals(401, response.statusCode());
        assertEquals("error", body.getString("type"));
        assertEquals("authentication_error", body.getJSONObject("error").getString("type"));
        assertEquals("invalid_api_key", body.getJSONObject("error").getString("code"));
    }

    /**
     * ms 缺 anthropic-version 在转发前被拒（400 invalid_request_error），不触达上游。
     */
    @Test
    public void messages_missing_version_is_rejected_before_forwarding() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", "sk-any")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(
                        messagesBody("any-model").toJSONString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject body = JSON.parseObject(response.body());

        log.info("ms 缺版本响应: status={}, body={}", response.statusCode(), body);
        assertEquals(400, response.statusCode());
        assertEquals("error", body.getString("type"));
        assertEquals("invalid_request_error", body.getJSONObject("error").getString("type"));
        assertEquals(0, upstream.getRequestCount());
    }

    /**
     * ms tools 字段不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void unsupported_tool_field_returns_400_unsupported_field() throws Exception {
        String model = "ms-tools-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONObject body = messagesBody(model);
            JSONArray tools = new JSONArray();
            JSONObject tool = new JSONObject();
            tool.put("name", "lookup");
            tools.add(tool);
            body.put("tools", tools);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms 不支持字段: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("error", error.getString("type"));
            assertEquals("invalid_request_error", error.getJSONObject("error").getString("type"));
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message").contains("tools"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms thinking 字段不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_thinking_field_returns_400_unsupported_field() throws Exception {
        String model = "ms-thinking-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONObject body = messagesBody(model);
            body.put("thinking", JSON.parseObject("{\"type\":\"enabled\",\"budget_tokens\":1024}"));

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms thinking 不支持字段: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message").contains("thinking"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms top_k 字段不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_top_k_field_returns_400_unsupported_field() throws Exception {
        String model = "ms-top-k-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONObject body = messagesBody(model);
            body.put("top_k", 42);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms top_k 不支持字段: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message").contains("top_k"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms content.cache_control 不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_cache_control_block_returns_400_unsupported_field() throws Exception {
        String model = "ms-cache-control-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONArray system = new JSONArray();
            JSONObject block = new JSONObject();
            block.put("type", "text");
            block.put("text", "be nice");
            block.put("cache_control", JSON.parseObject("{\"type\":\"ephemeral\"}"));
            system.add(block);
            JSONObject body = messagesBody(model);
            body.put("system", system);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms cache_control 不支持字段: status={}, body={}",
                    response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message")
                    .contains("content.cache_control"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms messages.name 不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_message_level_name_returns_400_unsupported_field() throws Exception {
        String model = "ms-message-name-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONObject body = messagesBody(model);
            body.getJSONArray("messages").getJSONObject(0).put("name", "alice");

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms 消息级未知字段: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message").contains("messages.name"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms content 块未知字段 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_unknown_content_block_field_returns_400_unsupported_field() throws Exception {
        String model = "ms-content-field-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONArray system = new JSONArray();
            JSONObject block = new JSONObject();
            block.put("type", "text");
            block.put("text", "be nice");
            block.put("vendor_flag", true);
            system.add(block);
            JSONObject body = messagesBody(model);
            body.put("system", system);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms 内容块未知字段: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message")
                    .contains("content.vendor_flag"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * CC 流式转 ms 时 stream_options 不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void chat_stream_options_unsupported_by_anthropic_returns_400() throws Exception {
        String model = "cc-stream-options-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONObject body = streamChatBody(model);
            body.put("stream_options", JSON.parseObject("{\"include_usage\":true}"));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", body, "text/event-stream");
            JSONObject error = JSON.parseObject(response.body());

            log.info("cc→ms stream_options 不支持字段: status={}, body={}",
                    response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message")
                    .contains("stream_options"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms 非空 annotations 不支持 → 400 unsupported_field，不触达上游。
     */
    @Test
    public void messages_non_empty_annotations_return_400_unsupported_field() throws Exception {
        String model = "ms-annotations-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            JSONArray system = new JSONArray();
            JSONObject block = new JSONObject();
            block.put("type", "text");
            block.put("text", "be nice");
            JSONArray annotations = new JSONArray();
            annotations.add(JSON.parseObject("{\"type\":\"citation\"}"));
            block.put("annotations", annotations);
            system.add(block);
            JSONObject body = messagesBody(model);
            body.put("system", system);

            HttpResponse<String> response = postMessages(apiKey.getKey(), body);
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms 非空 annotations: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message")
                    .contains("content.annotations"));
            assertEquals(0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 上游返回非空 annotations 时以 Anthropic 形状报 400 unsupported_field。
     */
    @Test
    public void upstream_non_empty_annotations_return_400_unsupported_field() throws Exception {
        String model = "ms-upstream-annotations-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"msg_annotations","type":"message","role":"assistant","model":"%s","content":[{"type":"text","text":"hello","annotations":[{"type":"citation"}]}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}
                            """.formatted(model)));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model), null);
            JSONObject error = JSON.parseObject(response.body());

            log.info("上游非空 annotations: status={}, body={}", response.statusCode(), error);
            assertEquals(400, response.statusCode());
            assertEquals("unsupported_field", error.getJSONObject("error").getString("code"));
            assertTrue(error.getJSONObject("error").getString("message")
                    .contains("content.annotations"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms 上游错误转换为 Anthropic 错误形状回传。
     */
    @Test
    public void messages_upstream_error_is_echoed_in_anthropic_shape() throws Exception {
        String model = "ms-upstream-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"error":{"message":"rate limited","type":"rate_limit_error"}}
                            """));

            HttpResponse<String> response = postMessages(apiKey.getKey(), messagesBody(model));
            JSONObject error = JSON.parseObject(response.body());

            log.info("ms 上游错误转换: status={}, body={}", response.statusCode(), error);
            assertEquals(429, response.statusCode());
            assertEquals("error", error.getString("type"));
            assertEquals("invalid_request_error", error.getJSONObject("error").getString("type"));
            assertEquals("rate limited", error.getJSONObject("error").getString("message"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * ms 上游畸形错误体仍以 Anthropic 形状回传。
     */
    @Test
    public void messages_malformed_upstream_error_still_uses_anthropic_shape() throws Exception {
        String model = "ms-bad-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(502)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("upstream exploded"));

            HttpResponse<String> response = postMessages(apiKey.getKey(), messagesBody(model));
            JSONObject body = JSON.parseObject(response.body());

            log.info("ms 畸形错误转换: status={}, body={}", response.statusCode(), body);
            assertEquals(502, response.statusCode());
            assertEquals("error", body.getString("type"));
            assertEquals("upstream exploded", body.getJSONObject("error").getString("message"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * rp 上游畸形错误体仍以 OpenAI 形状回传。
     */
    @Test
    public void responses_malformed_upstream_error_still_uses_openai_shape() throws Exception {
        String model = "rp-bad-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "anthropic", model, upstream.url("").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("upstream exploded"));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            JSONObject body = JSON.parseObject(response.body());

            log.info("rp 畸形错误转换: status={}, body={}", response.statusCode(), body);
            assertEquals(500, response.statusCode());
            assertEquals("upstream exploded", body.getJSONObject("error").getString("message"));
            assertEquals("invalid_request_error",
                    body.getJSONObject("error").getString("type"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * rp 原生上游畸形错误体同样以 OpenAI 形状回传。
     */
    @Test
    public void native_responses_malformed_error_also_uses_openai_shape() throws Exception {
        String model = "rp-native-bad-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model);
        Channel channel = createChannel(
                "openai", model, upstream.url("/v1").toString().replaceAll("/+$", ""), "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "text/plain")
                    .setBody("native exploded"));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/responses", responsesBody(model), null);
            JSONObject body = JSON.parseObject(response.body());

            log.info("rp 原生畸形错误: status={}, body={}", response.statusCode(), body);
            assertEquals(500, response.statusCode());
            assertEquals("native exploded", body.getJSONObject("error").getString("message"));
            assertEquals("invalid_request_error",
                    body.getJSONObject("error").getString("type"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /** 创建允许指定模型的协议测试密钥 */
    private ApiKey createApiKey(String model) {
        return apiKeyCase.create(ApiKey.builder()
                .name("协议测试密钥-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{model}))
                .quotaLimit(100L)
                .quotaUsed(0L)
                .build());
    }

    /** 创建指定渠道类型/模型的协议测试渠道 */
    private Channel createChannel(String type, String model, String baseUrl, String config) {
        return channelCase.create(Channel.builder()
                .name("协议测试渠道-" + UUID.randomUUID())
                .type(type)
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(10000)
                .weight(100)
                .config(config)
                .modelMapping("{}")
                .build());
    }

    /** 构造 /v1/messages 原生请求体 */
    private static JSONObject messagesBody(String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 100);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "hi");
        messages.add(message);
        body.put("messages", messages);
        return body;
    }

    /** 构造 chat/completions 请求体 */
    private static JSONObject chatBody(String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        JSONObject system = new JSONObject();
        system.put("role", "system");
        system.put("content", "be nice");
        messages.add(system);
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", "hi");
        messages.add(user);
        body.put("messages", messages);
        return body;
    }

    /** 构造 /v1/responses 原生请求体 */
    private static JSONObject responsesBody(String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("instructions", "be nice");
        JSONArray input = new JSONArray();
        JSONObject user = new JSONObject();
        user.put("role", "user");
        user.put("content", "hi");
        input.add(user);
        body.put("input", input);
        return body;
    }

    /** 在 chatBody 基础上开启流式 */
    private static JSONObject streamChatBody(String model) {
        JSONObject body = chatBody(model);
        body.put("stream", true);
        return body;
    }

    /** 以 x-api-key + anthropic-version 头发送 /v1/messages 请求 */
    private HttpResponse<String> postMessages(String apiKey, JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 以 Bearer 头发送 JSON 请求到指定路径 */
    private HttpResponse<String> postJson(String apiKey, String path, JSONObject body, String accept)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (accept != null) {
            builder.header("Accept", accept);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 构造 SSE 类型 MockResponse */
    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    /** 拼接指向本服务随机端口的完整 URI */
    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /** 等待异步记账写库到位（3 秒轮询兜底） */
    private void waitForQuota(String apiKeyId, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Long actual = apiKeyCase.get(apiKeyId).getQuotaUsed();
            if (actual != null && actual == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertEquals(Long.valueOf(expected), apiKeyCase.get(apiKeyId).getQuotaUsed());
    }

    /** 提取 SSE 文本中的所有 event 名 */
    private static List<String> sseEventNames(String body) {
        return Arrays.stream(body.split("\n\n"))
                .flatMap(block -> Arrays.stream(block.split("\n")))
                .filter(line -> line.startsWith("event:"))
                .map(line -> line.substring("event:".length()).trim())
                .toList();
    }

    /** 提取 SSE 文本中所有 data: 负载（去掉空块） */
    private static List<String> sseDataFrames(String body) {
        return Arrays.stream(body.split("\n\n"))
                .map(String::trim)
                .filter(block -> !block.isEmpty())
                .map(block -> Arrays.stream(block.split("\n"))
                        .filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring("data:".length()).trim())
                        .reduce((first, second) -> first + "\n" + second)
                        .orElse(""))
                .filter(data -> !data.isEmpty())
                .toList();
    }
}
