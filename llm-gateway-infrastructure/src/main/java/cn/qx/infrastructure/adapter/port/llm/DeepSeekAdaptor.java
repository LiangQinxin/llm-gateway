package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.model.valobj.TestResult;
import cn.qx.domain.channel.model.valobj.TokenUsage;
import cn.qx.infrastructure.adapter.port.llm.support.OpenAICompatSupport;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek 适配器——OpenAI 兼容协议透传。
 * <p>与 OpenAI 兼容渠道行为高度相似仍独立成类，保留厂商独立演化空间
 * （推理内容特判、错误码格式差异等）；通用兜底由 Custom 适配器承担。
 */
@Slf4j
@Component
public class DeepSeekAdaptor implements ILlmAdaptor {

    /** 连接超时（秒） */
    private static final int CONNECT_TIMEOUT_SECS = 10;

    @Override
    public String channelType() {
        return "deepseek";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("deepseek-chat", "deepseek-coder", "deepseek-reasoner");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.deepseek.com/v1";
    }

    @Override
    public TestResult test(ChannelConfig config) {
        long start = System.currentTimeMillis();
        try {
            RestClient client = restClient(config);
            return client.get().uri("/models").exchange((request, response) -> {
                long latency = System.currentTimeMillis() - start;
                int status = response.getStatusCode().value();
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                if (status >= 200 && status < 300) {
                    return TestResult.builder().success(true).message("连接成功").latencyMs(latency).build();
                }
                return TestResult.builder()
                        .success(false)
                        .message("HTTP " + status + ": " + truncate(body))
                        .latencyMs(latency)
                        .build();
            });
        } catch (Exception e) {
            log.warn("DeepSeek 连通性测试失败: {}", e.getMessage());
            return TestResult.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public ForwardResult forward(ProxyRequest request, ChannelConfig config) {
        JSONObject body = OpenAICompatSupport.applyModelMapping(request.getBody(), config.getModelMapping());
        RestClient client = restClient(config);
        return client.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toJSONString())
                .exchange((req, resp) -> {
                    int status = resp.getStatusCode().value();
                    String text = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    JSONObject json;
                    try {
                        json = JSON.parseObject(text);
                    } catch (Exception e) {
                        json = new JSONObject();
                        json.put("raw", text);
                    }
                    return ForwardResult.builder()
                            .statusCode(status)
                            .body(json)
                            .usage(extractUsage(json))
                            .build();
                });
    }

    @Override
    public StreamResult forwardStream(ProxyRequest request, ChannelConfig config) {
        JSONObject body = OpenAICompatSupport.applyModelMapping(request.getBody(), config.getModelMapping());
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
                .build();
        Request req = new Request.Builder()
                .url(trimSlash(config.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(body.toJSONString(), okhttp3.MediaType.get("application/json")))
                .build();
        try {
            Response resp = client.newCall(req).execute();
            InputStream stream = resp.body() != null ? resp.body().byteStream() : InputStream.nullInputStream();
            return StreamResult.builder().statusCode(resp.code()).body(stream).build();
        } catch (Exception e) {
            throw new IllegalStateException("流式转发失败: " + e.getMessage(), e);
        }
    }

    /** 按渠道配置构建非流式 HTTP 客户端（连接超时 + 读超时 = timeoutSecs） */
    private RestClient restClient(ChannelConfig config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_SECS * 1000);
        factory.setReadTimeout((int) Math.max(1, config.getTimeoutSecs()) * 1000);
        return RestClient.builder()
                .baseUrl(trimSlash(config.getBaseUrl()))
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 从上游响应体提取 Token 用量（无 usage 节点返回 null） */
    private static TokenUsage extractUsage(JSONObject json) {
        JSONObject usage = json.getJSONObject("usage");
        if (usage == null) {
            return null;
        }
        return TokenUsage.builder()
                .promptTokens(usage.getLongValue("prompt_tokens"))
                .completionTokens(usage.getLongValue("completion_tokens"))
                .totalTokens(usage.getLongValue("total_tokens"))
                .build();
    }

    /** 去掉 URL 尾部斜杠 */
    private static String trimSlash(String url) {
        return url != null ? url.replaceAll("/+$", "") : "";
    }

    /** 错误消息截断到 200 字符 */
    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
