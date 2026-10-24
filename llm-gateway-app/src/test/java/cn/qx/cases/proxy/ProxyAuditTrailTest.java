package cn.qx.cases.proxy;

import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * ProxyCase 全量数据面留痕真实链路测试。
 * <p>{@code @SpringBootTest} 起真实容器连本地 MySQL，MockWebServer 模拟上游：
 * 入站原始 body → ProxyCase 管道 → ProxyResult + RequestLog 落库。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ProxyAuditTrailTest {

    /** 被测代理用例 */
    @Resource
    private ProxyCase proxyCase;

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    /** 请求日志用例 */
    @Resource
    private IRequestLogCase requestLogCase;

    /** 渠道仓储：零启用渠道场景需要临时切换全部渠道状态 */
    @Resource
    private IChannelRepository channelRepository;

    /**
     * 成功非流式请求留一条完整日志：状态码、渠道、响应 choices、用量、trace 与耗时均落库。
     */
    @Test
    public void successful_unary_request_records_complete_request_log() throws Exception {
        String model = "audit-unary-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("审计成功用例-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-audit","object":"chat.completion",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},
                             "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}
                            """));
            upstream.start();
            Channel channel = channelCase.create(Channel.builder()
                    .name("审计成功用例-channel-" + UUID.randomUUID())
                    .type("openai")
                    .baseUrl(upstream.url("/v1").toString())
                    .apiKey("upstream-key")
                    .models(JSON.toJSONString(new String[]{model}))
                    .priority(10000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            try {
                String body = requestJson(model);
                log.info("审计成功用例入参: model={}, body={}", model, body);
                ProxyResult result = proxyCase.forward(
                        proxyCase.prepare(body, "application/json", "/v1/chat/completions"),
                        apiKey.getKey());
                log.info("审计成功用例出参: {}", JSON.toJSONString(result));
                assertEquals(200, result.getStatusCode());

                List<RequestLog> logs = logsFor(model);
                log.info("审计成功用例日志: {}", JSON.toJSONString(logs));
                assertEquals("成功请求应留一条日志", 1, logs.size());
                RequestLog requestLog = logs.get(0);
                assertEquals(Integer.valueOf(200), requestLog.getStatusCode());
                assertEquals(channel.getId(), requestLog.getChannelId());
                assertEquals(channel.getName(), requestLog.getChannelName());
                assertEquals(model, requestLog.getUpstreamModel());
                assertEquals(Integer.valueOf(0), requestLog.getIsRetry());
                assertEquals(Long.valueOf(3), requestLog.getPromptTokens());
                assertEquals(Long.valueOf(4), requestLog.getCompletionTokens());
                assertEquals(Long.valueOf(7), requestLog.getTotalTokens());
                assertNotNull("trace_id 应贯穿日志", requestLog.getTraceId());
                assertTrue("耗时应落库", requestLog.getDurationMs() >= 0);
                JSONArray choices = requestLog.responseChoicesJson();
                assertNotNull("成功响应应留存 choices", choices);
                assertEquals("response_choices 应保持同构",
                        "ok", choices.getJSONObject(0)
                                .getJSONObject("message").getString("content"));
            } finally {
                channelCase.delete(channel.getId());
                deleteLogsFor(model);
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 请求模型没有任何匹配渠道时返回 404，并落一条 model_not_found 日志。
     */
    @Test
    public void model_not_found_returns_404_and_records_log() {
        String model = "audit-not-found-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("审计404用例-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try {
            String body = requestJson(model);
            log.info("审计404用例入参: model={}, body={}", model, body);
            ProxyResult result = proxyCase.forward(
                    proxyCase.prepare(body, "application/json", "/v1/chat/completions"),
                    apiKey.getKey());
            log.info("审计404用例出参: {}", JSON.toJSONString(result));
            assertEquals(404, result.getStatusCode());
            assertTrue(result.getRaw().contains("model_not_found"));

            List<RequestLog> logs = logsFor(model);
            assertEquals("模型未命中也应留痕", 1, logs.size());
            assertEquals(Integer.valueOf(404), logs.get(0).getStatusCode());
            assertTrue(logs.get(0).getErrorMessage().contains("does not exist"));
        } finally {
            deleteLogsFor(model);
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 全库零启用渠道返回 503 no_available_channel，且不写任何请求日志。
     */
    @Test
    public void no_enabled_channel_returns_503_without_log() {
        String model = "audit-no-channel-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("审计503用例-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        List<Channel> enabled = channelRepository.listEnabledOrdered();
        try {
            enabled.forEach(channel -> channelCase.toggle(channel.getId(), false));
            String body = requestJson(model);
            log.info("审计503用例入参: model={}, body={}", model, body);
            ProxyResult result = proxyCase.forward(
                    proxyCase.prepare(body, "application/json", "/v1/chat/completions"),
                    apiKey.getKey());
            log.info("审计503用例出参: {}", JSON.toJSONString(result));
            assertEquals(503, result.getStatusCode());
            assertTrue(result.getRaw().contains("no_available_channel"));
            assertEquals("全库零启用渠道是唯一不留痕分支", 0, logsFor(model).size());
        } finally {
            enabled.forEach(channel -> channelCase.toggle(channel.getId(), true));
            deleteLogsFor(model);
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 首渠道 500、次渠道 200：日志标记 is_retry=1，并记录最终成功渠道与上游模型。
     */
    @Test
    public void failover_success_records_retry_and_final_channel() throws Exception {
        String model = "audit-failover-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("审计failover用例-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try (MockWebServer first = new MockWebServer();
             MockWebServer second = new MockWebServer()) {
            first.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":{\"message\":\"first failed\"}}"));
            second.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-second","choices":[{"index":0,
                             "message":{"role":"assistant","content":"second"},
                             "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}
                            """));
            first.start();
            second.start();
            Channel primary = channelCase.create(Channel.builder()
                    .name("审计failover-first-" + UUID.randomUUID())
                    .type("openai")
                    .baseUrl(first.url("/v1").toString())
                    .apiKey("first-key")
                    .models(JSON.toJSONString(new String[]{model}))
                    .priority(20000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            Channel secondary = channelCase.create(Channel.builder()
                    .name("审计failover-second-" + UUID.randomUUID())
                    .type("openai")
                    .baseUrl(second.url("/v1").toString())
                    .apiKey("second-key")
                    .models(JSON.toJSONString(new String[]{model}))
                    .priority(10000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            try {
                ProxyResult result = proxyCase.forward(
                        proxyCase.prepare(requestJson(model), "application/json",
                                "/v1/chat/completions"),
                        apiKey.getKey());
                log.info("审计failover用例出参: {}", JSON.toJSONString(result));
                assertEquals(200, result.getStatusCode());

                List<RequestLog> logs = logsFor(model);
                assertEquals(1, logs.size());
                assertEquals(Integer.valueOf(1), logs.get(0).getIsRetry());
                assertEquals(secondary.getId(), logs.get(0).getChannelId());
                assertEquals(secondary.getName(), logs.get(0).getChannelName());
                assertEquals(model, logs.get(0).getUpstreamModel());
            } finally {
                channelCase.delete(secondary.getId());
                channelCase.delete(primary.getId());
                deleteLogsFor(model);
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    /**
     * 非法 JSON 返回 400，日志模型使用 unknown 占位并保留原始字节。
     */
    @Test
    public void invalid_json_returns_400_and_records_unknown_model() {
        ProxyException error = assertThrows(ProxyException.class,
                () -> proxyCase.prepare("{not-json", "application/json",
                        "/v1/chat/completions"));
        log.info("非法 JSON 错误: status={}, code={}", error.getStatusCode(), error.getCode());
        assertEquals(400, error.getStatusCode());

        List<RequestLog> logs = logsFor("unknown");
        RequestLog matched = logs.stream()
                .filter(log -> log.getRequestBody() != null
                        && log.getRequestBody().contains("{not-json"))
                .findFirst()
                .orElse(null);
        assertNotNull("非法 JSON 也应留痕", matched);
        assertEquals(Integer.valueOf(400), matched.getStatusCode());
        try {
            if (matched != null) {
                requestLogCase.delete(matched.getId());
            }
        } finally {
            logsFor("unknown").stream()
                    .filter(log -> log.getRequestBody() != null
                            && log.getRequestBody().contains("{not-json"))
                    .forEach(log -> requestLogCase.delete(log.getId()));
        }
    }

    /**
     * 无效 API Key 返回 401，并在认证失败分支留痕。
     */
    @Test
    public void invalid_api_key_returns_401_and_records_log() {
        String model = "audit-invalid-key-" + UUID.randomUUID();
        ProxyRequest request = proxyCase.prepare(
                requestJson(model), "application/json", "/v1/chat/completions");
        ProxyException error = assertThrows(ProxyException.class,
                () -> proxyCase.forward(request, "invalid-" + UUID.randomUUID()));
        log.info("无效密钥错误: status={}, code={}", error.getStatusCode(), error.getCode());
        assertEquals(401, error.getStatusCode());

        List<RequestLog> logs = logsFor(model);
        assertEquals(1, logs.size());
        assertEquals(Integer.valueOf(401), logs.get(0).getStatusCode());
        assertTrue(logs.get(0).getErrorMessage().contains("invalid_api_key"));
        deleteLogsFor(model);
    }

    /**
     * 主入口鉴权先于安全检查：无效密钥 + 高风险 body 仍应返回 401，而不是 403 security_blocked。
     */
    @Test
    public void invalid_api_key_is_rejected_before_security_scan() {
        String model = "audit-auth-first-" + UUID.randomUUID();
        String body = "{\"model\":\"" + model
                + "\",\"stream\":false,\"messages\":[{\"role\":\"user\","
                + "\"content\":\"AKIA1234567890ABCDEF\"}]}";
        ProxyException error = assertThrows(ProxyException.class,
                () -> proxyCase.prepare(body, "application/json",
                        "/v1/chat/completions", "invalid-" + UUID.randomUUID()));
        log.info("鉴权优先错误: status={}, code={}", error.getStatusCode(), error.getCode());
        assertEquals(401, error.getStatusCode());
        assertEquals("invalid_api_key", error.getCode());
        List<RequestLog> logs = logsFor(model);
        assertEquals(1, logs.size());
        assertEquals(Integer.valueOf(401), logs.get(0).getStatusCode());
        deleteLogsFor(model);
    }

    private List<RequestLog> logsFor(String model) {
        return requestLogCase.page(RequestLogQuery.builder()
                .model(model)
                .page(1)
                .size(20)
                .build()).getRecords();
    }

    private void deleteLogsFor(String model) {
        logsFor(model).forEach(requestLog -> requestLogCase.delete(requestLog.getId()));
    }

    private static String requestJson(String model) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", "hello");
        messages.add(message);
        body.put("messages", messages);
        return body.toJSONString();
    }
}
