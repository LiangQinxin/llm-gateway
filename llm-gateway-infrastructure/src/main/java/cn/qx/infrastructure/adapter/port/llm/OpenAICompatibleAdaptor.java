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
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议适配器基类：透传请求、应用模型映射、注入 Bearer 鉴权。
 */
@Slf4j
public abstract class OpenAICompatibleAdaptor implements ILlmAdaptor {

    private static final int CONNECT_TIMEOUT_SECS = 10;

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
            log.warn("{} 连通性测试失败: {}", channelType(), e.getMessage());
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
                            .raw(text)
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
            okhttp3.Call call = client.newCall(req);
            Response resp = call.execute();
            InputStream stream = resp.body() != null ? resp.body().byteStream() : InputStream.nullInputStream();
            return StreamResult.builder().statusCode(resp.code()).body(stream).cancel(call).build();
        } catch (Exception e) {
            throw new IllegalStateException("流式转发失败: " + e.getMessage(), e);
        }
    }

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

    protected static String trimSlash(String url) {
        return url != null ? url.replaceAll("/+$", "") : "";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
