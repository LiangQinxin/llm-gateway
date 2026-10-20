package cn.qx.trigger.proxy;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.domain.channel.model.entity.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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

    private static final String BASE_URL = "http://127.0.0.1:8777/v1";
    private static final String API_KEY = "sk-waliapi-04dc74ce989d458e848a2c1616bf5611";
    private static final String MODEL = "deepseek-flash";

    @LocalServerPort
    private int port;

    @Resource
    private ChannelCase channelCase;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    public void missing_model_returns_openai_400() throws Exception {
        HttpResponse<String> response = sendString(
                "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}", "application/json");
        log.info("缺 model 响应: status={}, body={}", response.statusCode(), response.body());
        assertEquals(400, response.statusCode());
        assertEquals("application/json", contentType(response));
        assertTrue(response.body().contains("\"error\""));
    }

    @Test
    public void unknown_model_and_real_unary_forward() throws Exception {
        HttpResponse<String> notFound = sendString(
                "{\"model\":\"missing-" + UUID.randomUUID()
                        + "\",\"stream\":false,\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}",
                "application/json");
        log.info("未命中响应: status={}, body={}", notFound.statusCode(), notFound.body());
        assertEquals(404, notFound.statusCode());
        assertTrue(notFound.body().contains("model_not_found"));

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

    @Test
    public void accept_fallback_really_requests_and_streams_sse() throws Exception {
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

    private HttpResponse<String> sendString(String body, String accept) throws Exception {
        return client.send(request(body, accept), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest request(String body, String accept) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private Channel createChannel(String namePrefix) {
        return channelCase.create(Channel.builder()
                .name(namePrefix + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
    }

    private static String contentType(HttpResponse<?> response) {
        String value = response.headers().firstValue("Content-Type").orElse("");
        return value.split(";", 2)[0];
    }
}
