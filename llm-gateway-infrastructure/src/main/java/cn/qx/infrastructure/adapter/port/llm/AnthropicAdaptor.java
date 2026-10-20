package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.model.valobj.TestResult;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Anthropic 渠道适配器。
 * <p>本期管理面只要求连通性探测；转发协议转换在后续 proxy/adaptor 阶段实现。
 */
@Slf4j
@Component
public class AnthropicAdaptor implements ILlmAdaptor {

    private static final String DEFAULT_VERSION = "2023-06-01";

    @Override
    public String channelType() {
        return "anthropic";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.anthropic.com";
    }

    @Override
    public TestResult test(ChannelConfig config) {
        long start = System.currentTimeMillis();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(Math.max(1, config.getTimeoutSecs()), TimeUnit.SECONDS)
                .build();
        JSONObject extra = config.getExtra() == null ? new JSONObject() : config.getExtra();
        String version = extra.getString("anthropic_version");
        Request request = new Request.Builder()
                .url(trimSlash(config.getBaseUrl()) + "/v1/models")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", version == null || version.isBlank() ? DEFAULT_VERSION : version)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            long latency = System.currentTimeMillis() - start;
            boolean success = response.isSuccessful();
            return TestResult.builder()
                    .success(success)
                    .message(success ? "连接成功" : "HTTP " + response.code())
                    .latencyMs(latency)
                    .build();
        } catch (Exception e) {
            log.warn("Anthropic 连通性测试失败: {}", e.getMessage());
            return TestResult.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .latencyMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public ForwardResult forward(ProxyRequest request, ChannelConfig config) {
        throw new UnsupportedOperationException("Anthropic 转发协议转换尚未启用");
    }

    @Override
    public StreamResult forwardStream(ProxyRequest request, ChannelConfig config) {
        throw new UnsupportedOperationException("Anthropic 流式协议转换尚未启用");
    }

    private static String trimSlash(String url) {
        return url != null ? url.replaceAll("/+$", "") : "";
    }
}
