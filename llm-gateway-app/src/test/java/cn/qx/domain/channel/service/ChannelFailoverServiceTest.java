package cn.qx.domain.channel.service;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ChannelSelector 编排集成测试：真实 Spring 容器 + 本地 MySQL + MockWebServer 假上游。
 * <p>从 {@link IDispatchInvoker#invoke} 门面断言失败分类、per-attempt 模型烘焙与流式边界。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChannelFailoverServiceTest {

    private static final String API_KEY = "test-key";
    private static final String SUCCESS_BODY = """
            {"id":"chatcmpl-test","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":3,"total_tokens":5}}
            """;
    private static final String STREAM_BODY = """
            data: {"id":"chatcmpl-test","choices":[{"index":0,"delta":{"content":"ok"}}]}

            data: [DONE]

            """;

    @Resource
    private IDispatchInvoker dispatchInvoker;

    @Resource
    private ChannelCase channelCase;

    /**
     * 500 后切换兜底渠道，并在每次尝试前按当前渠道重新烘焙 body.model。
     */
    @Test
    public void failover_500_rebakes_model_per_attempt() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(error(500, "primary failed"));
        server.enqueue(success());
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            Channel primary = create(server, model, "upstream-primary", 1000, 10, channelIds);
            Channel backup = create(server, model, "upstream-backup", 900, 1, channelIds);
            log.info("failover 渠道: primary={}, backup={}", primary.getId(), backup.getId());

            ProxyRequest request = request(model, false);
            DispatchOutcome outcome = dispatchInvoker.invoke(request);
            log.info("500 failover 结果: status={}, attempts={}",
                    outcome.getUnary().getStatusCode(), outcome.getErrorMessage());

            assertTrue(outcome.isRouted());
            assertEquals(200, outcome.getUnary().getStatusCode());
            assertEquals(2, server.getRequestCount());
            assertEquals("upstream-primary", modelOf(server.takeRequest()));
            assertEquals("upstream-backup", modelOf(server.takeRequest()));
            assertEquals("最后一次尝试模型应保留在请求体",
                    "upstream-backup", request.getBody().getString("model"));
        } finally {
            cleanup(server, channelIds);
        }
    }

    /**
     * 429/401/403/500 均切换下一渠道。
     */
    @Test
    public void retryable_statuses_fail_over_to_backup() throws Exception {
        for (int status : List.of(429, 401, 403, 500)) {
            MockWebServer server = new MockWebServer();
            server.start();
            server.enqueue(error(status, "retryable " + status));
            server.enqueue(success());
            String model = uniqueModel();
            List<String> channelIds = new ArrayList<>();
            try {
                create(server, model, "primary-" + status, 1000, 10, channelIds);
                create(server, model, "backup-" + status, 900, 1, channelIds);

                DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false));
                log.info("retryable failover: upstreamStatus={}, finalStatus={}, requests={}",
                        status, outcome.getUnary().getStatusCode(), server.getRequestCount());

                assertTrue(outcome.isRouted());
                assertEquals("HTTP " + status + " 应切换兜底", 200,
                        outcome.getUnary().getStatusCode());
                assertEquals("应尝试主备两个渠道", 2, server.getRequestCount());
            } finally {
                cleanup(server, channelIds);
            }
        }
    }

    /**
     * 请求本身错误（400）直接透传，不触发 failover。
     */
    @Test
    public void request_error_is_passed_through_without_failover() throws Exception {
        MockWebServer server = new MockWebServer();
        server.start();
        server.enqueue(error(400, "bad request"));
        server.enqueue(success());
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            create(server, model, "primary-400", 1000, 10, channelIds);
            create(server, model, "backup-400", 900, 1, channelIds);

            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false));
            log.info("400 不切换: status={}, requests={}",
                    outcome.getUnary().getStatusCode(), server.getRequestCount());

            assertEquals(400, outcome.getUnary().getStatusCode());
            assertTrue(outcome.getUnary().getRaw().contains("bad request"));
            assertEquals("400 不应尝试备用渠道", 1, server.getRequestCount());
        } finally {
            cleanup(server, channelIds);
        }
    }

    /**
     * 流式连接建立前网络异常可切换兜底，并逐字节透传兜底 SSE。
     */
    @Test
    public void stream_connection_failure_fails_over_before_bytes() throws Exception {
        MockWebServer backupServer = new MockWebServer();
        backupServer.start();
        backupServer.enqueue(stream());
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            create("http://127.0.0.1:1/v1", model, "unreachable", 1000, 10, channelIds);
            create(backupServer, model, "stream-backup", 900, 1, channelIds);

            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, true));
            String sse = new String(outcome.getStreaming().getBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            log.info("流式连接失败切换: status={}, sse={}",
                    outcome.getStreaming().getStatusCode(), sse);

            assertTrue(outcome.isRouted());
            assertNotNull(outcome.getStreaming());
            assertEquals(200, outcome.getStreaming().getStatusCode());
            assertTrue(sse.contains("[DONE]"));
            assertEquals("兜底上游应收到一次请求", 1, backupServer.getRequestCount());
        } finally {
            cleanup(backupServer, channelIds);
        }
    }

    /**
     * 流式 HTTP 5xx 在字节开始前切换，兜底 2xx SSE 正常返回。
     */
    @Test
    public void stream_5xx_fails_over_before_passthrough() throws Exception {
        MockWebServer primaryServer = new MockWebServer();
        MockWebServer backupServer = new MockWebServer();
        primaryServer.start();
        backupServer.start();
        primaryServer.enqueue(error(500, "stream primary failed"));
        backupServer.enqueue(stream());
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            create(primaryServer, model, "stream-primary", 1000, 10, channelIds);
            create(backupServer, model, "stream-backup", 900, 1, channelIds);

            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, true));
            String sse = new String(outcome.getStreaming().getBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            log.info("流式 500 切换: status={}, sse={}",
                    outcome.getStreaming().getStatusCode(), sse);

            assertEquals(200, outcome.getStreaming().getStatusCode());
            assertTrue(sse.contains("[DONE]"));
            assertEquals(1, primaryServer.getRequestCount());
            assertEquals(1, backupServer.getRequestCount());
        } finally {
            primaryServer.shutdown();
            cleanup(backupServer, channelIds);
        }
    }

    /**
     * 全部为网络异常时映射 502 upstream_unavailable，并汇总完整尝试序列。
     */
    @Test
    public void all_network_failures_map_to_502_and_include_attempt_summary() {
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            String first = create("http://127.0.0.1:1/v1", model, "dead-a", 1000, 10, channelIds)
                    .getId();
            String second = create("http://127.0.0.1:1/v1", model, "dead-b", 900, 1, channelIds)
                    .getId();

            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false));
            log.info("全网络失败: status={}, error={}",
                    outcome.getUnary().getStatusCode(), outcome.getErrorMessage());

            assertTrue(outcome.isRouted());
            assertEquals(502, outcome.getUnary().getStatusCode());
            assertTrue(outcome.getUnary().getRaw().contains("upstream_unavailable"));
            assertNotNull(outcome.getErrorMessage());
            assertTrue(outcome.getErrorMessage().contains(first));
            assertTrue(outcome.getErrorMessage().contains(second));
            assertEquals("同一渠道不得重复尝试", 1, occurrences(outcome.getErrorMessage(), first));
            assertEquals("同一渠道不得重复尝试", 1, occurrences(outcome.getErrorMessage(), second));
        } finally {
            channelIds.forEach(channelCase::delete);
        }
    }

    /**
     * 全部 HTTP 失败时透传最后一个渠道的状态码与响应体，不替换成 502。
     */
    @Test
    public void all_http_failures_pass_through_last_upstream_error() throws Exception {
        MockWebServer firstServer = new MockWebServer();
        MockWebServer secondServer = new MockWebServer();
        firstServer.start();
        secondServer.start();
        firstServer.enqueue(error(500, "first failed"));
        secondServer.enqueue(error(503, "last failed"));
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            create(firstServer, model, "http-first", 1000, 10, channelIds);
            create(secondServer, model, "http-last", 900, 1, channelIds);

            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false));
            log.info("全 HTTP 失败: status={}, raw={}, error={}",
                    outcome.getUnary().getStatusCode(), outcome.getUnary().getRaw(),
                    outcome.getErrorMessage());

            assertEquals(503, outcome.getUnary().getStatusCode());
            assertTrue(outcome.getUnary().getRaw().contains("last failed"));
            assertTrue(outcome.getErrorMessage().contains("HTTP 500"));
            assertTrue(outcome.getErrorMessage().contains("HTTP 503"));
        } finally {
            firstServer.shutdown();
            cleanup(secondServer, channelIds);
        }
    }

    /**
     * 同优先级 5:3:2 权重在多请求下近似分摊，且每个渠道都参与。
     */
    @Test
    public void same_priority_distributes_requests_by_weight() throws Exception {
        List<MockWebServer> servers = List.of(new MockWebServer(), new MockWebServer(), new MockWebServer());
        int[] weights = {5, 3, 2};
        String model = uniqueModel();
        List<String> channelIds = new ArrayList<>();
        try {
            for (int i = 0; i < servers.size(); i++) {
                MockWebServer server = servers.get(i);
                server.setDispatcher(alwaysSuccess());
                server.start();
                create(server, model, "weighted-" + weights[i], 1000, weights[i], channelIds);
            }

            int requests = 300;
            for (int i = 0; i < requests; i++) {
                DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false));
                assertEquals(200, outcome.getUnary().getStatusCode());
            }
            int[] actual = servers.stream().mapToInt(MockWebServer::getRequestCount).toArray();
            log.info("5:3:2 分发结果: requests={}, actual={}", requests, actual);

            assertDistribution(actual[0], requests, 0.50);
            assertDistribution(actual[1], requests, 0.30);
            assertDistribution(actual[2], requests, 0.20);
        } finally {
            for (MockWebServer server : servers) {
                server.shutdown();
            }
            channelIds.forEach(channelCase::delete);
        }
    }

    /**
     * 无匹配模型保持 notFound 语义。
     */
    @Test
    public void no_matching_channel_keeps_model_not_found_outcome() {
        DispatchOutcome outcome = dispatchInvoker.invoke(request(uniqueModel(), false));
        log.info("未命中结果: {}", JSON.toJSONString(outcome));
        assertFalse(outcome.isRouted());
        assertTrue(outcome.getErrorMessage().contains("does not exist"));
    }

    private Channel create(MockWebServer server,
                           String model,
                           String upstreamModel,
                           int priority,
                           int weight,
                           List<String> channelIds) {
        return create(server.url("/v1").toString(), model, upstreamModel, priority, weight, channelIds);
    }

    private Channel create(String baseUrl,
                           String model,
                           String upstreamModel,
                           int priority,
                           int weight,
                           List<String> channelIds) {
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-failover-" + UUID.randomUUID())
                .type("openai")
                .baseUrl(baseUrl)
                .apiKey(API_KEY)
                .models("[\"" + model + "\"]")
                .modelMapping("{\"" + model + "\":\"" + upstreamModel + "\"}")
                .priority(priority)
                .weight(weight)
                .config("{}")
                .build());
        channelIds.add(channel.getId());
        return channel;
    }

    private static ProxyRequest request(String model, boolean stream) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", stream);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "failover");
        messages.add(message);
        body.put("messages", messages);
        return new ProxyRequest(model, body, stream);
    }

    private static MockResponse error(int status, String message) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"" + message + "\",\"type\":\"test_error\",\"code\":\""
                        + status + "\"}}");
    }

    private static MockResponse success() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SUCCESS_BODY);
    }

    private static MockResponse stream() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(STREAM_BODY);
    }

    private static Dispatcher alwaysSuccess() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return success();
            }
        };
    }

    private static String modelOf(RecordedRequest request) {
        return JSON.parseObject(request.getBody().readUtf8()).getString("model");
    }

    private static void assertDistribution(int actual, int requests, double expected) {
        double ratio = actual / (double) requests;
        log.info("权重分布: expected={}, actual={}", expected, ratio);
        assertEquals("权重分布偏离过大", expected, ratio, 0.12);
    }

    private static int occurrences(String value, String target) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }

    private static String uniqueModel() {
        return "failover-" + UUID.randomUUID();
    }

    private void cleanup(MockWebServer server, List<String> channelIds) throws Exception {
        try {
            server.shutdown();
        } finally {
            channelIds.forEach(channelCase::delete);
        }
    }
}
