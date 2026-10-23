package cn.qx.trigger.gateway;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.cases.proxy.IProxyCase;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.identity.model.entity.ApiKey;
import com.alibaba.fastjson2.JSON;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 数据面鉴权真实容器测试：本地 HTTP 入口 + MockWebServer 假上游 + 本地 MySQL。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DataPlaneAuthTest {

    /** 随机注入的应用端口 */
    @LocalServerPort
    private int port;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    /** 数据面代理用例 */
    @Resource
    private IProxyCase proxyCase;

    /** 本测试专属假上游 */
    private MockWebServer upstream;

    /** 数据面 HTTP 客户端 */
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
     * 缺密钥请求返回 OpenAI 风格 401，且鉴权 filter 失败时不得触达上游。
     */
    @Test
    public void missing_key_returns_openai_401_without_calling_upstream() throws Exception {
        upstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"chatcmpl-test","object":"chat.completion","choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """));

        JSONObject body = new JSONObject();
        body.put("model", "auth-test-model");
        body.put("stream", false);
        body.put("messages", JSON.parseArray("""
                [{"role":"user","content":"hi"}]
                """));

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(dataPlaneUri("/v1/chat/completions"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        log.info("缺密钥响应: status={}, body={}, upstreamRequests={}",
                response.statusCode(), response.body(), upstream.getRequestCount());
        assertEquals(401, response.statusCode());
        assertEquals("application/json", contentType(response));
        assertTrue("错误码应为 invalid_api_key", response.body().contains("invalid_api_key"));
        assertEquals("filter 失败不应触达上游", 0, upstream.getRequestCount());
    }

    /**
     * 公开路径免鉴权可达性：health、models、mcp、mcp/sse 等无需密钥即可访问。
     */
    @Test
    public void public_paths_are_reachable_without_key() throws Exception {
        HttpResponse<String> health = get("/health");
        HttpResponse<String> apiHealth = get("/api/v1/health");
        HttpResponse<String> models = get("/v1/models");
        HttpResponse<String> mcp = get("/mcp");
        HttpResponse<String> mcpSse = get("/mcp/sse");
        HttpResponse<String> modelSubpath = get("/v1/models/anything");

        log.info("公开路径响应: health={}, apiHealth={}, models={}, mcp={}, mcpSse={}, modelSubpath={}",
                health.statusCode(), apiHealth.statusCode(), models.statusCode(), mcp.statusCode(),
                mcpSse.statusCode(), modelSubpath.statusCode());
        assertEquals(200, health.statusCode());
        assertEquals(200, apiHealth.statusCode());
        assertEquals(200, models.statusCode());
        assertTrue("mcp 不应被鉴权 filter 拦截", mcp.statusCode() != 401);
        assertTrue("mcp/sse 不应被鉴权 filter 拦截", mcpSse.statusCode() != 401);
        assertTrue("models 子路径不应被鉴权 filter 拦截", modelSubpath.statusCode() != 401);
    }

    /**
     * 有效密钥链路：转发触达上游、Authorization 换上游 key、2xx 按 total_tokens 记账。
     */
    @Test
    public void valid_key_forwards_and_records_unary_usage() throws Exception {
        String model = "auth-usage-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("数据面记账测试-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{model}))
                .quotaLimit(100L)
                .quotaUsed(0L)
                .build());
        Channel channel = channelCase.create(Channel.builder()
                .name("数据面上游测试-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(upstream.url("/v1").toString().replaceAll("/+$", ""))
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
        log.info("创建鉴权测试数据: key={}, channel={}", JSON.toJSONString(apiKey), JSON.toJSONString(channel));

        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-test","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}
                            """));

            HttpResponse<String> response = postChat(apiKey.getKey(), model, false, "");
            RecordedRequest upstreamRequest = upstream.takeRequest(Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            log.info("有效密钥响应: status={}, body={}, upstreamRequest={}",
                    response.statusCode(), response.body(), upstreamRequest);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"choices\""));
            assertEquals("/v1/chat/completions", upstreamRequest.getPath());
            assertEquals("Authorization 应使用上游 key",
                    "Bearer upstream-key", upstreamRequest.getHeader("Authorization"));
            assertEquals("2xx 后应按 total_tokens 记账",
                    Long.valueOf(7), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 流式请求：默认注入 stream_options.include_usage=true，按 usage 帧记账。
     */
    @Test
    public void stream_request_injects_usage_option_and_records_sse_usage() throws Exception {
        String model = "auth-stream-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("流式记账测试-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{model}))
                .quotaLimit(100L)
                .quotaUsed(0L)
                .build());
        Channel channel = channelCase.create(Channel.builder()
                .name("流式上游测试-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(upstream.url("/v1").toString().replaceAll("/+$", ""))
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());

        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"id":"chunk-1","choices":[{"index":0,"delta":{"content":"ok"}}]}

                            data: {"id":"chunk-2","choices":[],"usage":{"prompt_tokens":4,"completion_tokens":5,"total_tokens":9}}

                            data: [DONE]

                            """));

            HttpResponse<String> response = postChat(apiKey.getKey(), model, true, "text/event-stream");
            RecordedRequest upstreamRequest = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(upstreamRequest.getBody().readUtf8());

            log.info("流式响应: status={}, body={}, upstreamBody={}",
                    response.statusCode(), response.body(), upstreamBody);
            assertEquals(200, response.statusCode());
            assertTrue("客户端仍应收到 SSE", response.body().contains("data: [DONE]"));
            assertEquals("默认应注入 include_usage",
                    Boolean.TRUE, upstreamBody.getJSONObject("stream_options").getBoolean("include_usage"));
            waitForQuota(apiKey.getId(), 9L);
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 客户端显式 stream_options 应原样保留，不被网关覆盖。
     */
    @Test
    public void client_stream_options_are_preserved() throws Exception {
        String model = "auth-preserve-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 0L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(sseResponse("data: [DONE]\n\n"));
            JSONObject body = chatBody(model, true);
            JSONObject streamOptions = new JSONObject();
            streamOptions.put("include_usage", false);
            body.put("stream_options", streamOptions);

            HttpResponse<String> response = postJson(apiKey.getKey(), "/v1/chat/completions", body, "text/event-stream");
            RecordedRequest upstreamRequest = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(upstreamRequest.getBody().readUtf8());

            log.info("客户端 stream_options 保留: status={}, upstreamBody={}",
                    response.statusCode(), upstreamBody);
            assertEquals(200, response.statusCode());
            assertEquals(Boolean.FALSE,
                    upstreamBody.getJSONObject("stream_options").getBoolean("include_usage"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 渠道配置 inject_stream_options=false 时不得注入 stream_options。
     */
    @Test
    public void channel_can_disable_stream_options_injection() throws Exception {
        String model = "auth-no-inject-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 0L, 0L);
        Channel channel = createChannel(model, "{\"inject_stream_options\":false}");
        try {
            upstream.enqueue(sseResponse("data: [DONE]\n\n"));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model, true), "text/event-stream");
            RecordedRequest upstreamRequest = upstream.takeRequest(
                    Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            JSONObject upstreamBody = JSON.parseObject(upstreamRequest.getBody().readUtf8());

            log.info("渠道关闭注入: status={}, upstreamBody={}", response.statusCode(), upstreamBody);
            assertEquals(200, response.statusCode());
            assertTrue("渠道关闭后不得注入", !upstreamBody.containsKey("stream_options"));
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 鉴权拒绝状态码矩阵。
     * <pre>
     * 无效密钥 → 401 invalid_api_key；模型越权 → 404 model_not_found；配额耗尽 → 403 insufficient_quota
     * </pre>
     */
    @Test
    public void authentication_rejections_use_required_status_codes() throws Exception {
        String model = "auth-reject-" + UUID.randomUUID();
        Channel channel = createChannel(model, "{}");
        ApiKey wrongModelKey = apiKeyCase.create(ApiKey.builder()
                .name("模型拒绝-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{"another-model"}))
                .quotaLimit(0L)
                .quotaUsed(0L)
                .build());
        ApiKey exhaustedKey = createApiKey(model, 5L, 5L);
        try {
            HttpResponse<String> invalid = postJson(
                    "sk-invalid-" + UUID.randomUUID(), "/v1/chat/completions",
                    chatBody(model, false), "application/json");
            HttpResponse<String> modelDenied = postJson(
                    wrongModelKey.getKey(), "/v1/chat/completions",
                    chatBody(model, false), "application/json");
            HttpResponse<String> exhausted = postJson(
                    exhaustedKey.getKey(), "/v1/chat/completions",
                    chatBody(model, false), "application/json");

            log.info("认证拒绝: invalid={}, modelDenied={}, exhausted={}",
                    invalid.statusCode(), modelDenied.statusCode(), exhausted.statusCode());
            assertEquals(401, invalid.statusCode());
            assertTrue(invalid.body().contains("invalid_api_key"));
            assertEquals(404, modelDenied.statusCode());
            assertTrue(modelDenied.body().contains("model_not_found"));
            assertEquals(403, exhausted.statusCode());
            assertTrue(exhausted.body().contains("insufficient_quota"));
            assertEquals("所有拒绝都不应触达上游", 0, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(wrongModelKey.getId());
            apiKeyCase.delete(exhaustedKey.getId());
        }
    }

    /**
     * 上游 4xx 错误透传且不记账：非 2xx 不扣减配额。
     */
    @Test
    public void upstream_error_does_not_record_quota() throws Exception {
        String model = "auth-upstream-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 100L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"error":{"message":"rate limited","type":"rate_limit_error"},"usage":{"total_tokens":13}}
                            """));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model, false), "application/json");

            log.info("上游错误透传: status={}, body={}", response.statusCode(), response.body());
            assertEquals(429, response.statusCode());
            assertTrue(response.body().contains("rate limited"));
            assertEquals(Long.valueOf(0), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 渠道白名单本轮不强制：allowedChannels 与实际渠道不符时仍放行（待后续轮次收紧）。
     */
    @Test
    public void channel_whitelist_is_not_enforced_in_this_round() throws Exception {
        String model = "auth-channel-whitelist-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("渠道白名单延后-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{model}))
                .allowedChannels("[\"some-other-channel\"]")
                .quotaLimit(0L)
                .quotaUsed(0L)
                .build());
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"choices\":[]}"));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model, false), "application/json");

            log.info("渠道白名单本轮不强制: status={}, body={}", response.statusCode(), response.body());
            assertEquals(200, response.statusCode());
            assertEquals(1, upstream.getRequestCount());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 畸形 SSE 帧完整透传，且解析不出 usage 时不记账。
     */
    @Test
    public void malformed_sse_is_passed_through_without_recording() throws Exception {
        String model = "auth-bad-sse-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 100L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(sseResponse("""
                    data: {not-json}

                    data: [DONE]

                    """));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model, true), "text/event-stream");

            log.info("畸形 SSE 透传: status={}, body={}", response.statusCode(), response.body());
            assertEquals(200, response.statusCode());
            assertTrue("格式异常也应完整透传", response.body().contains("{not-json}"));
            assertTrue(response.body().contains("data: [DONE]"));
            assertEquals(Long.valueOf(0), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 流式上游错误（SSE 内 error + 非 2xx）透传且不记账。
     */
    @Test
    public void streaming_upstream_error_does_not_record_quota() throws Exception {
        String model = "auth-stream-error-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 100L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(sseResponse("""
                    data: {"error":{"message":"upstream failed"},"usage":{"total_tokens":11}}

                    data: [DONE]

                    """).setResponseCode(500));

            HttpResponse<String> response = postJson(
                    apiKey.getKey(), "/v1/chat/completions", chatBody(model, true), "text/event-stream");

            log.info("流式上游错误: status={}, body={}", response.statusCode(), response.body());
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("upstream failed"));
            assertEquals(Long.valueOf(0), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 客户端中途断开：流未正常完成时不记账。
     */
    @Test
    public void client_cancellation_does_not_record_quota() throws Exception {
        String model = "auth-cancel-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 100L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            upstream.enqueue(sseResponse("""
                    data: {"usage":{"total_tokens":12}}

                    data: [DONE]

                    """).setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS));

            var proxyRequest = proxyCase.prepare(
                    chatBody(model, true).toJSONString(), "text/event-stream", "/v1/chat/completions");
            var result = proxyCase.forward(proxyRequest, apiKey.getKey());
            ResponseBodyEmitter disconnectedEmitter = new ResponseBodyEmitter() {
                @Override
                public void send(Object object) throws IOException {
                    throw new IOException("client disconnected");
                }
            };
            proxyCase.write(result, "/v1/chat/completions",
                    disconnectedEmitter, new MockHttpServletResponse());

            Thread.sleep(100);
            log.info("客户端取消后 quotaUsed={}", apiKeyCase.get(apiKey.getId()).getQuotaUsed());
            assertEquals(Long.valueOf(0), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 三条数据面入口按同名路径转发。
     * <pre>
     * /v1/completions、/v1/embeddings、/v1/responses → 上游相同路径；
     * embeddings 请求不得被补 stream 字段
     * </pre>
     */
    @Test
    public void completions_embeddings_and_responses_use_matching_upstream_paths() throws Exception {
        String model = "auth-paths-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey(model, 100L, 0L);
        Channel channel = createChannel(model, "{}");
        try {
            String[] responseBodies = {
                    """
                    {"id":"cmpl","choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                    """,
                    """
                    {"id":"emb","data":[],"usage":{"prompt_tokens":2,"total_tokens":2}}
                    """,
                    """
                    {"id":"resp","output":[],"usage":{"input_tokens":3,"output_tokens":1,"total_tokens":4}}
                    """
            };
            String[] paths = {"/v1/completions", "/v1/embeddings", "/v1/responses"};
            for (int i = 0; i < paths.length; i++) {
                upstream.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody(responseBodies[i]));
            }
            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("input", "hi");
                HttpResponse<String> response = postJson(apiKey.getKey(), path, body, "application/json");
                assertEquals("入口 " + path + " 应成功", 200, response.statusCode());
            }

            for (int i = 0; i < paths.length; i++) {
                String path = paths[i];
                RecordedRequest upstreamRequest = upstream.takeRequest(
                        Duration.ofSeconds(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
                log.info("入口 {} 对应上游路径: {}", path, upstreamRequest.getPath());
                assertEquals(path, upstreamRequest.getPath());
                JSONObject forwarded = JSON.parseObject(upstreamRequest.getBody().readUtf8());
                if ("/v1/embeddings".equals(path)) {
                    assertTrue("embeddings 请求不应被补 stream 字段", !forwarded.containsKey("stream"));
                }
            }
            waitForQuota(apiKey.getId(), 8L);
        } finally {
            channelCase.delete(channel.getId());
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /** 创建指定模型/配额状态的调用密钥 */
    private ApiKey createApiKey(String model, long quotaLimit, long quotaUsed) {
        return apiKeyCase.create(ApiKey.builder()
                .name("数据面测试-" + UUID.randomUUID())
                .allowedModels(JSON.toJSONString(new String[]{model}))
                .quotaLimit(quotaLimit)
                .quotaUsed(quotaUsed)
                .build());
    }

    /** 创建指向 MockWebServer 假上游的 openai 渠道 */
    private Channel createChannel(String model, String config) {
        return channelCase.create(Channel.builder()
                .name("数据面上游-" + UUID.randomUUID())
                .type("openai")
                .baseUrl(upstream.url("/v1").toString().replaceAll("/+$", ""))
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(10000)
                .weight(100)
                .config(config)
                .modelMapping("{}")
                .build());
    }

    /** 构造 chat/completions 请求体 */
    private static JSONObject chatBody(String model, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", stream);
        body.put("messages", JSON.parseArray("""
                [{"role":"user","content":"hi"}]
                """));
        return body;
    }

    /** 构造 SSE 类型 MockResponse */
    private static MockResponse sseResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body);
    }

    /** 向数据面入口发送 JSON 请求（apiKey 为空时不带 Authorization） */
    private HttpResponse<String> postJson(String apiKey, String path, JSONObject body, String accept) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(dataPlaneUri(path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        if (accept != null && !accept.isBlank()) {
            builder.header("Accept", accept);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 快捷发送 chat/completions 请求 */
    private HttpResponse<String> postChat(String apiKey, String model, boolean stream, String accept) throws Exception {
        return postJson(apiKey, "/v1/chat/completions", chatBody(model, stream), accept);
    }

    /** 发送 GET 请求 */
    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(dataPlaneUri(path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 拼接指向本服务随机端口的完整 URI */
    private URI dataPlaneUri(String path) {
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

    /** 提取 Content-Type 主类型（去掉参数段） */
    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0];
    }
}
