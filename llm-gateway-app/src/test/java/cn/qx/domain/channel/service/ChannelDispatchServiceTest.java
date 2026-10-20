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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IDispatchInvoker 真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChannelDispatchServiceTest {

    private static final String BASE_URL = "http://127.0.0.1:8777/v1";
    private static final String API_KEY = "sk-waliapi-04dc74ce989d458e848a2c1616bf5611";
    private static final String UPSTREAM_MODEL = "deepseek-flash";

    @Resource
    private IDispatchInvoker dispatchInvoker;

    @Resource
    private ChannelCase channelCase;

    @Test
    public void dispatch_routes_maps_forwards_and_streams() throws Exception {
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-dispatch-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[\"deepseek-flash\"]")
                .modelMapping("""
                        {
                          "mapped-model":"deepseek-flash",
                          "array-model":["deepseek-flash","deepseek-flash"]
                        }
                        """)
                .priority(10000)
                .weight(100)
                .config("{}")
                .build());
        Channel decoy = channelCase.create(Channel.builder()
                .name("测试-dispatch-decoy-" + UUID.randomUUID())
                .type("openai")
                .baseUrl("http://127.0.0.1:1/v1")
                .apiKey("invalid")
                .models("[\"deepseek-flash\"]")
                .priority(9000)
                .weight(1)
                .config("{}")
                .modelMapping("{}")
                .build());
        Channel chain = channelCase.create(Channel.builder()
                .name("测试-dispatch-chain-" + UUID.randomUUID())
                .type("deepseek")
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models("[]")
                .modelMapping("""
                        {
                          "chain-a":"deepseek-flash",
                          "deepseek-flash":"must-not-be-used"
                        }
                        """)
                .priority(5000)
                .weight(1)
                .config("{}")
                .build());
        log.info("创建路由测试渠道: {}", JSON.toJSONString(channel));
        try {
            DispatchOutcome direct = dispatchInvoker.invoke(
                    request(UPSTREAM_MODEL, false, "direct"));
            log.info("直连 dispatch: {}", JSON.toJSONString(direct));
            assertTrue("直连模型应命中", direct.isRouted());
            assertEquals("直连转发应 200", 200, direct.getUnary().getStatusCode());
            assertNotNull("非流式应保留原始响应文本", direct.getUnary().getRaw());
            assertNotNull("直连响应应含 choices", direct.getUnary().getBody().getJSONArray("choices"));

            ProxyRequest mappedRequest = request("mapped-model", false, "mapped");
            DispatchOutcome mapped = dispatchInvoker.invoke(mappedRequest);
            log.info("字符串映射 dispatch: request={}, outcome={}",
                    JSON.toJSONString(mappedRequest), JSON.toJSONString(mapped));
            assertTrue("字符串映射应命中", mapped.isRouted());
            assertEquals("映射应预烘焙 upstream model",
                    UPSTREAM_MODEL, mappedRequest.getBody().getString("model"));
            assertEquals("映射转发应 200", 200, mapped.getUnary().getStatusCode());

            ProxyRequest arrayRequest = request("array-model", false, "array");
            DispatchOutcome array = dispatchInvoker.invoke(arrayRequest);
            log.info("数组映射 dispatch: request={}, outcome={}",
                    JSON.toJSONString(arrayRequest), JSON.toJSONString(array));
            assertTrue("数组映射应命中", array.isRouted());
            assertEquals("数组映射应预烘焙到上游模型",
                    UPSTREAM_MODEL, arrayRequest.getBody().getString("model"));
            assertEquals("数组映射转发应 200", 200, array.getUnary().getStatusCode());

            ProxyRequest chainRequest = request("chain-a", false, "chain");
            DispatchOutcome chainOutcome = dispatchInvoker.invoke(chainRequest);
            log.info("链式映射 dispatch: request={}, outcome={}",
                    JSON.toJSONString(chainRequest), JSON.toJSONString(chainOutcome));
            assertTrue("链式映射应命中", chainOutcome.isRouted());
            assertEquals("映射只能采样一次，不得二次改写",
                    UPSTREAM_MODEL, chainRequest.getBody().getString("model"));
            assertEquals("链式映射转发应 200", 200, chainOutcome.getUnary().getStatusCode());

            DispatchOutcome notFound = dispatchInvoker.invoke(
                    request("missing-" + UUID.randomUUID(), false, "missing"));
            assertFalse("未命中不应 routed", notFound.isRouted());
            assertNotNull("未命中应带错误信息", notFound.getErrorMessage());

            DispatchOutcome streaming = dispatchInvoker.invoke(
                    request(UPSTREAM_MODEL, true, "stream"));
            log.info("流式 dispatch: status={}, cancel={}",
                    streaming.getStreaming().getStatusCode(), streaming.getStreaming().getCancel());
            assertTrue("流式应命中", streaming.isRouted());
            assertNotNull("流式响应应带 cancel 句柄", streaming.getStreaming().getCancel());
            String sse = new String(streaming.getStreaming().getBody().readAllBytes(), StandardCharsets.UTF_8);
            log.info("dispatch SSE（截断）: {}", sse.length() > 240 ? sse.substring(0, 240) : sse);
            assertTrue("应透传 data: 帧", sse.contains("data:"));
            assertTrue("应透传 [DONE]", sse.contains("[DONE]"));
        } finally {
            channelCase.delete(channel.getId());
            channelCase.delete(decoy.getId());
            channelCase.delete(chain.getId());
        }
    }

    @Test
    public void probe_only_anthropic_channel_is_not_routed() {
        String model = "anthropic-only-" + UUID.randomUUID();
        Channel channel = channelCase.create(Channel.builder()
                .name("测试-dispatch-anthropic-" + UUID.randomUUID())
                .type("anthropic")
                .baseUrl("http://127.0.0.1:8777")
                .apiKey(API_KEY)
                .models("[\"" + model + "\"]")
                .priority(10000)
                .weight(100)
                .config("{}")
                .modelMapping("{}")
                .build());
        try {
            DispatchOutcome outcome = dispatchInvoker.invoke(request(model, false, "anthropic"));
            assertFalse("仅探测能力适配器不应进入数据面", outcome.isRouted());
            assertNotNull(outcome.getErrorMessage());
        } finally {
            channelCase.delete(channel.getId());
        }
    }

    private static ProxyRequest request(String model, boolean stream, String content) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", stream);
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", content);
        messages.add(message);
        body.put("messages", messages);
        return new ProxyRequest(model, body, stream);
    }
}
