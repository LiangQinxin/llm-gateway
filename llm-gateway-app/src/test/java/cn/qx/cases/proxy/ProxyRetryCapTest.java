package cn.qx.cases.proxy;

import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.proxy.ProxyResult;
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
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * dispatch.retry-count 总尝试预算真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "dispatch.retry-count=1")
public class ProxyRetryCapTest {

    @Resource
    private ProxyCase proxyCase;

    @Resource
    private ChannelCase channelCase;

    @Resource
    private ApiKeyCase apiKeyCase;

    @Resource
    private IRequestLogCase requestLogCase;

    /**
     * retry-count=1 → 最多尝试 2 个渠道；三渠道全 500 时透传最后一个 HTTP 500 并留一条日志。
     */
    @Test
    public void retry_count_caps_total_attempts_and_records_last_http_error() throws Exception {
        String model = "retry-cap-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("retry-cap-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());
        try (MockWebServer first = new MockWebServer();
             MockWebServer second = new MockWebServer();
             MockWebServer third = new MockWebServer()) {
            first.enqueue(http500("first"));
            second.enqueue(http500("second"));
            third.enqueue(http500("third"));
            first.start();
            second.start();
            third.start();
            Channel firstChannel = createChannel(model, first, 30000, "first");
            Channel secondChannel = createChannel(model, second, 20000, "second");
            Channel thirdChannel = createChannel(model, third, 10000, "third");
            try {
                ProxyResult result = proxyCase.forward(
                        proxyCase.prepare(requestJson(model), "application/json",
                                "/v1/chat/completions"),
                        apiKey.getKey());
                log.info("retry-count=1 出参: {}", JSON.toJSONString(result));
                assertEquals("最后一次 HTTP 错误应透传", 500, result.getStatusCode());
                assertEquals("最多 retry+1 次尝试", 1, first.getRequestCount());
                assertEquals("最多 retry+1 次尝试", 1, second.getRequestCount());
                assertEquals("超过预算的第三渠道不应被调用", 0, third.getRequestCount());

                List<RequestLog> logs = requestLogCase.page(RequestLogQuery.builder()
                        .model(model).page(1).size(20).build()).getRecords();
                assertEquals(1, logs.size());
                assertEquals(Integer.valueOf(500), logs.get(0).getStatusCode());
                assertEquals(Integer.valueOf(1), logs.get(0).getIsRetry());
                assertEquals(secondChannel.getId(), logs.get(0).getChannelId());
                assertTrue("最终错误应汇总完整尝试序列",
                        logs.get(0).getErrorMessage().contains(firstChannel.getId())
                                && logs.get(0).getErrorMessage().contains(secondChannel.getId()));
            } finally {
                channelCase.delete(thirdChannel.getId());
                channelCase.delete(secondChannel.getId());
                channelCase.delete(firstChannel.getId());
                requestLogCase.page(RequestLogQuery.builder()
                                .model(model).page(1).size(20).build())
                        .getRecords()
                        .forEach(requestLog -> requestLogCase.delete(requestLog.getId()));
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }

    private Channel createChannel(String model,
                                  MockWebServer upstream,
                                  int priority,
                                  String name) {
        return channelCase.create(Channel.builder()
                .name("retry-cap-" + name + "-" + UUID.randomUUID())
                .type("openai")
                .baseUrl(upstream.url("/v1").toString())
                .apiKey(name + "-key")
                .models(JSON.toJSONString(new String[]{model}))
                .priority(priority)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
    }

    private static MockResponse http500(String name) {
        return new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"" + name + " failed\"}}");
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
