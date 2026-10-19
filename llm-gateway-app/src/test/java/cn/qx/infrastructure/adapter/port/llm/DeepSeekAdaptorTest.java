package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.IChannelAdaptorRegistry;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ModelMapping;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.model.valobj.TestResult;
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
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * DeepSeek 适配器真实端点全链路测试。
 * <p>上游：本地 OpenAI 兼容网关 http://127.0.0.1:8777，模型 deepseek-flash。
 * <p>{@code @SpringBootTest} 起真实容器连本地 MySQL(llm_api)，不 mock，入参出参 log+JSON 打印。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class DeepSeekAdaptorTest {

    /** 上游基础 URL（含 /v1） */
    private static final String BASE_URL = "http://127.0.0.1:8777/v1";

    /** 上游 API 密钥 */
    private static final String API_KEY = "sk-waliapi-04dc74ce989d458e848a2c1616bf5611";

    /** 上游模型名 */
    private static final String MODEL = "deepseek-flash";

    /** 适配器注册表 */
    @Resource
    private IChannelAdaptorRegistry registry;

    /** 构造测试用渠道配置 */
    private ChannelConfig config() {
        return ChannelConfig.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .models(List.of(MODEL))
                .modelMapping(new JSONObject())
                .extra(new JSONObject())
                .timeoutSecs(60)
                .build();
    }

    @Test
    public void test_channel_connectivity() {
        ILlmAdaptor adaptor = registry.getAdaptor("deepseek");
        log.info("适配器: type={}, defaultBaseUrl={}, defaultModels={}",
                adaptor.channelType(), adaptor.defaultBaseUrl(), adaptor.defaultModels());
        TestResult result = adaptor.test(config());
        log.info("连通性测试结果: {}", JSON.toJSONString(result));
        assertTrue("连通性测试应成功（上游 127.0.0.1:8777 需在线）", result.isSuccess());
    }

    @Test
    public void forward_chat_completions() {
        ILlmAdaptor adaptor = registry.getAdaptor("deepseek");
        ForwardResult result = adaptor.forward(chatRequest(MODEL, "你好，请用一句话介绍自己"), config());
        log.info("转发结果: status={}, usage={}, body={}",
                result.getStatusCode(), result.getUsage(), result.getBody());
        assertEquals("HTTP 200", 200, result.getStatusCode());
        assertNotNull("响应应含 choices", result.getBody().getJSONArray("choices"));
        assertTrue("choices 不应为空", result.getBody().getJSONArray("choices").size() > 0);
    }

    @Test
    public void forward_with_string_model_mapping() {
        // 字符串映射：下游请求 gpt-4o → 上游改写为 deepseek-flash
        ILlmAdaptor adaptor = registry.getAdaptor("deepseek");
        JSONObject mapping = new JSONObject();
        mapping.put("gpt-4o", MODEL);

        ForwardResult result = adaptor.forward(chatRequest("gpt-4o", "hi"),
                ChannelConfig.builder()
                        .baseUrl(BASE_URL).apiKey(API_KEY)
                        .models(List.of(MODEL))
                        .modelMapping(mapping)
                        .extra(new JSONObject())
                        .timeoutSecs(60)
                        .build());
        log.info("字符串映射转发结果: status={}, body={}", result.getStatusCode(), result.getBody());
        assertEquals("映射后应 200", 200, result.getStatusCode());
    }

    @Test
    public void forward_with_array_model_mapping() {
        // 数组映射：采样在路由阶段一次完成并预烘焙进 body，适配器透传
        ILlmAdaptor adaptor = registry.getAdaptor("deepseek");
        JSONObject mapping = new JSONObject();
        JSONArray targets = new JSONArray();
        targets.add(MODEL);
        targets.add(MODEL);   // 双目标等权（本地仅 deepseek-flash，用同值演示采样机制）
        mapping.put("gpt-4o", targets);

        ModelMapping modelMapping = new ModelMapping(mapping);
        String upstream = modelMapping.resolve("gpt-4o", new Random());
        log.info("数组映射采样结果: requested=gpt-4o → upstream={}", upstream);
        assertTrue("采样结果应在目标数组中", targets.contains(upstream));

        ForwardResult result = adaptor.forward(chatRequest(upstream, "hi"),
                ChannelConfig.builder()
                        .baseUrl(BASE_URL).apiKey(API_KEY)
                        .models(List.of(MODEL))
                        .modelMapping(mapping)
                        .extra(new JSONObject())
                        .timeoutSecs(60)
                        .build());
        log.info("数组映射转发结果: status={}, body={}", result.getStatusCode(), result.getBody());
        assertEquals("预烘焙后应 200", 200, result.getStatusCode());
    }

    @Test
    public void forward_stream_sse() throws Exception {
        // 流式转发：字节流透传，SSE 帧直到 [DONE]
        ILlmAdaptor adaptor = registry.getAdaptor("deepseek");
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("stream", true);
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", "你好");
        messages.add(userMsg);
        body.put("messages", messages);

        StreamResult result = adaptor.forwardStream(new ProxyRequest(MODEL, body, true), config());
        assertEquals("SSE 流应 200", 200, result.getStatusCode());
        String sse = new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8);
        log.info("SSE 流内容（截断）: {}", sse.length() > 300 ? sse.substring(0, 300) : sse);
        assertTrue("应含 data: 帧", sse.contains("data:"));
        assertTrue("应以 [DONE] 结束", sse.contains("[DONE]"));
    }

    /** 构造一条 chat/completions 请求体 */
    private ProxyRequest chatRequest(String model, String content) {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", false);
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", content);
        messages.add(userMsg);
        body.put("messages", messages);
        return new ProxyRequest(model, body, false);
    }
}
