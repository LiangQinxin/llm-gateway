package cn.qx.trigger.gateway;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.identity.model.entity.ApiKey;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
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
 * ProxyController 真实 Tomcat 异步端点测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProxyControllerTest {

    /** 上游模型名 */
    private static final String MODEL = "proxy-controller-" + UUID.randomUUID();

    /** 随机注入的 HTTP 服务端口 */
    @LocalServerPort
    private int port;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    /** 当前测试使用的新密钥 */
    private ApiKey apiKey;

    /** 每个用例独立的假上游 */
    private MockWebServer upstream;

    /** HTTP 测试客户端（连接超时 10 秒） */
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 每个测试用例前创建专属调用密钥 */
    @Before
    public void createCallerKey() throws Exception {
        apiKey = apiKeyCase.create(ApiKey.builder()
                .name("测试-proxy-controller-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        upstream = new MockWebServer();
        upstream.start();
    }

    /** 每个测试用例后清理调用密钥 */
    @After
    public void deleteCallerKey() {
        if (upstream != null) {
            try {
                upstream.shutdown();
            } catch (Exception e) {
                log.warn("关闭 mock 上游失败: {}", e.getMessage());
            }
        }
        if (apiKey != null) {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 缺 model 请求应返回 OpenAI 风格 400 错误。
     */
    @Test
    public void missing_model_returns_openai_400() throws Exception {
        HttpResponse<String> response = sendString(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", "application/json");
        log.info("缺 model 响应: status={}, body={}", response.statusCode(), response.body());
        assertEquals(400, response.statusCode());
        assertEquals("application/json", contentType(response));
        assertTrue(response.body().contains("\"error\""));
    }

    /**
     * 未知模型返回 404 model_not_found；真实非流式转发返回 200 且含 choices。
     */
    @Test
    public void unknown_model_and_real_unary_forward() throws Exception {
        HttpResponse<String> notFound = sendString(
                "{\"model\":\"missing-" + UUID.randomUUID()
                        + "\",\"stream\":false,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                "application/json");
        log.info("未命中响应: status={}, body={}", notFound.statusCode(), notFound.body());
        assertEquals(404, notFound.statusCode());
        assertTrue(notFound.body().contains("model_not_found"));

        upstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"chatcmpl-controller","choices":[{"index":0,
                         "message":{"role":"assistant","content":"ok"},
                         "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """));
        Channel channel = createChannel("测试-proxy-controller-");
        try {
            HttpResponse<String> unary = sendString(
                    "{\"model\":\"" + MODEL
                            + "\",\"stream\":false,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                    "*/*");
            log.info("非流式响应: status={}, body={}", unary.statusCode(), unary.body());
            assertEquals(200, unary.statusCode());
            assertEquals("application/json", contentType(unary));
            assertTrue(unary.body().contains("\"choices\""));
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    /**
     * Accept: text/event-stream 兜底：请求体缺省 stream 时按流式发起并透传 SSE 至 [DONE]。
     */
    @Test
    public void accept_fallback_really_requests_and_streams_sse() throws Exception {
        upstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"id":"chunk-1","choices":[{"index":0,"delta":{"content":"ok"}}]}

                        data: [DONE]

                        """));
        Channel channel = createChannel("测试-proxy-stream-");
        try {
            HttpRequest request = request(
                    "{\"model\":\"" + MODEL
                            + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                    "text/event-stream");
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode());
            assertTrue("Content-Type 应为 SSE",
                    response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line).append('\n');
                    if (line.contains("[DONE]")) {
                        break;
                    }
                }
            }
            log.info("Controller SSE（截断）: {}",
                    body.length() > 240 ? body.substring(0, 240) : body);
            assertTrue("应含 data: 帧", body.toString().contains("data:"));
            assertTrue("应含 [DONE]", body.toString().contains("[DONE]"));
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    /**
     * 开流前连接失败兜底：上游不可达时降级为 JSON 502 upstream_error。
     */
    @Test
    public void pre_stream_connection_failure_falls_back_to_json() throws Exception {
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-proxy-broken-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl("http://127.0.0.1:1/v1")
                .apiKey("invalid")
                .models("[\"broken-model\"]")
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
        try {
            HttpResponse<String> response = sendString(
                    "{\"model\":\"broken-model\",\"stream\":true,"
                            + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                    "text/event-stream");
            log.info("开流前失败响应: status={}, body={}", response.statusCode(), response.body());
            assertEquals(502, response.statusCode());
            assertEquals("application/json", contentType(response));
            assertTrue(response.body().contains("upstream_error"));
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    /** 发送请求体并返回字符串响应 */
    private HttpResponse<String> sendString(String body, String accept) throws Exception {
        return client.send(request(body, accept), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 构造指向本服务 /v1/chat/completions 的 HTTP 请求 */
    private HttpRequest request(String body, String accept) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .header("Authorization", "Bearer " + apiKey.getKey())
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    /** 创建一条指向真实上游的 deepseek 渠道 */
    private Channel createChannel(String namePrefix) {
        return channelCase.create(Channel.builder()
                .name(namePrefix + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(upstream.url("/v1").toString())
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{MODEL}))
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
    }

    /** 提取 Content-Type 主类型（去掉参数段） */
    private static String contentType(HttpResponse<?> response) {
        String value = response.headers().firstValue("Content-Type").orElse("");
        return value.split(";", 2)[0];
    }
}
