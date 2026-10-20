package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.IForwardingAdaptor;
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
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容协议适配器基类：统一使用 OkHttp 完成探测、非流式与流式请求。
 */
@Slf4j
public abstract class OpenAICompatibleAdaptor implements ILlmAdaptor, IForwardingAdaptor {

    private static final int CONNECT_TIMEOUT_SECS = 10;
    private static final int MIN_STREAM_TIMEOUT_SECS = 600;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    @Override
    public TestResult test(ChannelConfig config) {
        long start = System.currentTimeMillis();
        Request request = new Request.Builder()
                .url(trimSlash(config.getBaseUrl()) + "/models")
                .header("Authorization", "Bearer " + config.getApiKey())
                .get()
                .build();
        try (Response response = client(config, false).newCall(request).execute()) {
            long latency = System.currentTimeMillis() - start;
            String body = response.body() == null
                    ? ""
                    : new String(response.body().bytes(), StandardCharsets.UTF_8);
            if (response.isSuccessful()) {
                return TestResult.builder().success(true).message("连接成功").latencyMs(latency).build();
            }
            return TestResult.builder()
                    .success(false)
                    .message("HTTP " + response.code() + ": " + truncate(body))
                    .latencyMs(latency)
                    .build();
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
        Request httpRequest = new Request.Builder()
                .url(trimSlash(config.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();
        try (Response response = client(config, false).newCall(httpRequest).execute()) {
            String text = response.body() == null
                    ? ""
                    : new String(response.body().bytes(), StandardCharsets.UTF_8);
            JSONObject json;
            try {
                json = JSON.parseObject(text);
            } catch (Exception e) {
                json = new JSONObject();
                json.put("raw", text);
            }
            return ForwardResult.builder()
                    .statusCode(response.code())
                    .body(json)
                    .raw(text)
                    .usage(extractUsage(json))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("非流式转发失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StreamResult forwardStream(ProxyRequest request, ChannelConfig config) {
        JSONObject body = OpenAICompatSupport.applyModelMapping(request.getBody(), config.getModelMapping());
        Request httpRequest = new Request.Builder()
                .url(trimSlash(config.getBaseUrl()) + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();
        try {
            Call call = client(config, true).newCall(httpRequest);
            Response response = call.execute();
            InputStream stream = response.body() != null
                    ? response.body().byteStream()
                    : InputStream.nullInputStream();
            return StreamResult.builder()
                    .statusCode(response.code())
                    .body(stream)
                    .cancel(call)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("流式转发失败: " + e.getMessage(), e);
        }
    }

    private OkHttpClient client(ChannelConfig config, boolean stream) {
        long configured = Math.max(1, config.getTimeoutSecs());
        long readTimeout = stream ? Math.max(MIN_STREAM_TIMEOUT_SECS, configured) : configured;
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
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
