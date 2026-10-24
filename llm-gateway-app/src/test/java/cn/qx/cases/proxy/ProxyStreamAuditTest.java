package cn.qx.cases.proxy;

import cn.qx.api.IRequestLogCase;
import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.identity.model.entity.ApiKey;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ProxyCase 流式 exactly-once 三分支真实 HTTP 测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProxyStreamAuditTest {

    @LocalServerPort
    private int port;

    @Resource
    private ChannelCase channelCase;

    @Resource
    private ApiKeyCase apiKeyCase;

    @Resource
    private IRequestLogCase requestLogCase;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 正常流式完成留一条 200 日志：choices 由 delta 重建、usage 落库并记账。
     */
    @Test
    public void completed_stream_records_reconstructed_choices_and_usage() throws Exception {
        String model = "audit-stream-ok-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey("audit-stream-ok-key-");
        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(sseBody()));
            upstream.start();
            Channel channel = createChannel(model, upstream, "audit-stream-ok-channel-");
            try {
                HttpResponse<String> response = postChat(apiKey.getKey(), model);
                log.info("流式成功响应: status={}, body={}", response.statusCode(), response.body());
                assertEquals(200, response.statusCode());
                assertTrue(response.body().contains("data: [DONE]"));

                RequestLog requestLog = waitForLog(model, 200, 5000);
                assertEquals(Integer.valueOf(200), requestLog.getStatusCode());
                assertEquals(Integer.valueOf(1), requestLog.getStream());
                assertEquals("sse", requestLog.getProtocolType());
                assertEquals("Hello", content(requestLog));
                assertEquals(Long.valueOf(2), requestLog.getPromptTokens());
                assertEquals(Long.valueOf(3), requestLog.getCompletionTokens());
                assertEquals(Long.valueOf(5), requestLog.getTotalTokens());
                assertEquals(Long.valueOf(5), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
            } finally {
                channelCase.delete(channel.getId());
                deleteLogsFor(model);
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 已发字节后客户端中断：留 499 + 已产生的 choices 快照，且不按 2xx 扣配额。
     */
    @Test
    public void cancelled_stream_records_499_snapshot_without_usage_charge() throws Exception {
        String model = "audit-stream-cancel-" + UUID.randomUUID();
        ApiKey apiKey = createApiKey("audit-stream-cancel-key-");
        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody("""
                            data: {"choices":[{"index":0,"delta":{"content":"Hel"}}]}

                            data: {"choices":[{"index":0,"delta":{"content":"lo from upstream"}}]}

                            """)
                    .throttleBody(4, 10, TimeUnit.MILLISECONDS));
            upstream.start();
            Channel channel = createChannel(model, upstream, "audit-stream-cancel-channel-");
            try {
                HttpRequest request = request(apiKey.getKey(), model);
                HttpResponse<InputStream> response = client.send(
                        request, HttpResponse.BodyHandlers.ofInputStream());
                assertEquals(200, response.statusCode());
                StringBuilder received = new StringBuilder();
                try (InputStreamReader reader =
                             new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
                    int value;
                    while ((value = reader.read()) != -1) {
                        received.append((char) value);
                        if (received.toString().contains("\"Hel\"")) {
                            break;
                        }
                    }
                }
                log.info("客户端主动断开前已读: {}", received);

                RequestLog requestLog = waitForLog(model, 499, 5000);
                log.info("流式断开日志: {}", JSON.toJSONString(requestLog));
                assertEquals(Integer.valueOf(499), requestLog.getStatusCode());
                assertTrue(requestLog.getErrorMessage().contains("client_cancelled"));
                assertTrue(content(requestLog).startsWith("Hel"));
                assertEquals("499 不按 2xx 扣配额",
                        Long.valueOf(0), apiKeyCase.get(apiKey.getId()).getQuotaUsed());
            } finally {
                channelCase.delete(channel.getId());
                deleteLogsFor(model);
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    private ApiKey createApiKey(String namePrefix) {
        return apiKeyCase.create(ApiKey.builder()
                .name(namePrefix + UUID.randomUUID())
                .quotaLimit(100L)
                .quotaUsed(0L)
                .build());
    }

    private Channel createChannel(String model, MockWebServer upstream, String namePrefix) {
        return channelCase.create(Channel.builder()
                .name(namePrefix + UUID.randomUUID())
                .type("openai")
                .baseUrl(upstream.url("/v1").toString())
                .apiKey("upstream-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
    }

    private HttpResponse<String> postChat(String apiKey, String model) throws Exception {
        return client.send(request(apiKey, model),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest request(String apiKey, String model) {
        return HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(
                        requestJson(model), StandardCharsets.UTF_8))
                .build();
    }

    private RequestLog waitForLog(String model, int statusCode, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            RequestLog matched = logsFor(model).stream()
                    .filter(item -> Integer.valueOf(statusCode).equals(item.getStatusCode()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                return matched;
            }
            Thread.sleep(50);
        }
        RequestLog matched = logsFor(model).stream()
                .filter(item -> Integer.valueOf(statusCode).equals(item.getStatusCode()))
                .findFirst()
                .orElse(null);
        assertNotNull("等待 " + statusCode + " 日志超时", matched);
        return matched;
    }

    private String content(RequestLog requestLog) {
        JSONArray choices = requestLog.responseChoicesJson();
        assertNotNull(choices);
        return choices.getJSONObject(0).getJSONObject("message").getString("content");
    }

    private List<RequestLog> logsFor(String model) {
        return requestLogCase.page(RequestLogQuery.builder()
                .model(model).page(1).size(20).build()).getRecords();
    }

    private void deleteLogsFor(String model) {
        logsFor(model).forEach(log -> requestLogCase.delete(log.getId()));
    }

    private static String sseBody() {
        return """
                data: {"id":"chunk-1","choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}

                data: {"id":"chunk-2","choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}

                data: [DONE]

                """;
    }

    private static String requestJson(String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", true);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "hello");
        messages.add(message);
        body.put("messages", messages);
        return body.toJSONString();
    }
}
