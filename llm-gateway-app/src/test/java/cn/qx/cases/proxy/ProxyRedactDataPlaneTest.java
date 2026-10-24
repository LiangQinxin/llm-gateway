package cn.qx.cases.proxy;

import cn.qx.cases.channel.ChannelCase;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SANITIZE 档真实转发测试：上游实际收到的 body 必须是脱敏后的内容。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "security.audit.mode=redact")
public class ProxyRedactDataPlaneTest {

    @Resource
    private ProxyCase proxyCase;

    @Resource
    private ChannelCase channelCase;

    @Resource
    private ApiKeyCase apiKeyCase;

    @Resource
    private IRequestLogCase requestLogCase;

    @Resource
    private ISecurityFindingRepository securityFindingRepository;

    @Test
    @Transactional
    public void redacted_body_is_actually_forwarded_upstream() throws Exception {
        String secret = "sk-proj-abcdefghijklmnopqrstuvwx";
        String model = "redact-data-plane-" + UUID.randomUUID();
        ApiKey apiKey = apiKeyCase.create(ApiKey.builder()
                .name("redact-data-plane-key-" + UUID.randomUUID())
                .quotaLimit(0L)
                .build());

        try (MockWebServer upstream = new MockWebServer()) {
            upstream.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"ok\",\"choices\":[],\"usage\":{\"total_tokens\":1}}"));
            upstream.start();
            Channel channel = channelCase.create(Channel.builder()
                    .name("redact-data-plane-channel-" + UUID.randomUUID())
                    .type("openai")
                    .baseUrl(upstream.url("/v1").toString())
                    .apiKey("upstream-key")
                    .models("[\"" + model + "\"]")
                    .priority(10000)
                    .weight(100)
                    .config("{}")
                    .modelMapping("{}")
                    .build());
            try {
                String body = "{\"model\":\"" + model
                        + "\",\"stream\":false,\"messages\":[{\"role\":\"user\","
                        + "\"content\":\"use " + secret + "\"}]}";
                ProxyRequest request = proxyCase.prepare(
                        body, "application/json", "/v1/chat/completions");
                proxyCase.forward(request, apiKey.getKey());

                RecordedRequest recorded = upstream.takeRequest(5, TimeUnit.SECONDS);
                assertNotNull(recorded);
                String forwarded = recorded.getBody().readUtf8();
                log.info("SANITIZE 实际上游 body: {}", forwarded);
                assertFalse(forwarded.contains(secret));
                assertTrue(forwarded.contains("sk-p****uvwx"));

                List<RequestLog> logs = requestLogCase.page(RequestLogQuery.builder()
                        .model(model).page(1).size(10).build()).getRecords();
                assertEquals(1, logs.size());
                assertEquals(Integer.valueOf(1), logs.get(0).getSanitized());
                assertEquals("Sanitize", logs.get(0).getSecurityAction());
                assertTrue(logs.get(0).getRequestBody().contains(secret));
                assertTrue(securityFindingRepository.countByLogId(logs.get(0).getId()) > 0);
            } finally {
                channelCase.delete(channel.getId());
            }
        } finally {
            apiKeyCase.delete(apiKey.getId());
        }
    }
}
