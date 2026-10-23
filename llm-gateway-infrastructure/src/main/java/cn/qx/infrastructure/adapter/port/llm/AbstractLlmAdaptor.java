package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.IForwardingAdaptor;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.model.valobj.TestResult;
import cn.qx.domain.channel.model.valobj.TokenUsage;
import cn.qx.domain.proxy.support.MessageSanitizer;
import cn.qx.infrastructure.gateway.GatewayHttpClient;
import cn.qx.infrastructure.adapter.port.llm.codec.ProtocolCodecs;
import cn.qx.types.exception.UnsupportedProtocolFieldException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 渠道适配器模板：有序端点选择、共享 HTTP 执行与响应 usage 提取。
 */
@Slf4j
public abstract class AbstractLlmAdaptor implements ILlmAdaptor, IForwardingAdaptor {

    /** JSON 媒体类型 */
    protected static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    /** Anthropic 版本缺省值 */
    protected static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    /** 共享 HTTP 客户端 */
    @Resource
    protected GatewayHttpClient gatewayHttpClient;

    @Override
    public TestResult test(ChannelConfig config) {
        long start = System.currentTimeMillis();
        NativeEndpoint endpoint = primaryEndpoint();
        String path = modelPath();
        Request request = requestBuilder(config, endpoint, joinUrl(config.getBaseUrl(), path))
                .get()
                .build();
        try (Response response = gatewayHttpClient.newCall(request, false, config.getTimeoutSecs()).execute()) {
            String body = response.body() == null
                    ? ""
                    : new String(response.body().bytes(), StandardCharsets.UTF_8);
            return TestResult.builder()
                    .success(response.isSuccessful())
                    .message(response.isSuccessful() ? "连接成功" : "HTTP " + response.code() + ": " + truncate(body))
                    .latencyMs(System.currentTimeMillis() - start)
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
        NativeEndpoint target = targetEndpoint(request);
        JSONObject converted = ProtocolCodecs.convertRequest(
                request.getProtocol(), target.getProtocol(), request.getBody());
        JSONObject body = prepareBody(converted, config, false, target.getProtocol());
        Request httpRequest = requestBuilder(
                config, target, joinUrl(config.getBaseUrl(), wirePath(request, target)))
                .post(RequestBody.create(serializeBody(request, body), JSON_MEDIA_TYPE))
                .build();
        try (Response response = gatewayHttpClient.newCall(
                httpRequest, false, config.getTimeoutSecs()).execute()) {
            String text = response.body() == null
                    ? ""
                    : new String(response.body().bytes(), StandardCharsets.UTF_8);
            String downstream = response.isSuccessful()
                    ? convertResponse(request.getProtocol(), target.getProtocol(), text)
                    : ProtocolCodecs.convertError(
                            request.getProtocol(), target.getProtocol(), response.code(), text);
            JSONObject json = parseOrRaw(downstream);
            return ForwardResult.builder()
                    .statusCode(response.code())
                    .body(json)
                    .raw(downstream)
                    .usage(response.isSuccessful()
                            ? extractUsage(request.getProtocol(), target.getProtocol(), json)
                            : null)
                    .build();
        } catch (UnsupportedProtocolFieldException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("非流式转发失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StreamResult forwardStream(ProxyRequest request, ChannelConfig config) {
        NativeEndpoint target = targetEndpoint(request);
        JSONObject converted = ProtocolCodecs.convertRequest(
                request.getProtocol(), target.getProtocol(), request.getBody());
        JSONObject body = prepareBody(converted, config, true, target.getProtocol());
        Request httpRequest = requestBuilder(
                config, target, joinUrl(config.getBaseUrl(), wirePath(request, target)))
                .post(RequestBody.create(serializeBody(request, body), JSON_MEDIA_TYPE))
                .build();
        try {
            Call call = gatewayHttpClient.newCall(httpRequest, true, config.getTimeoutSecs());
            Response response = call.execute();
            InputStream upstream = response.body() != null
                    ? response.body().byteStream()
                    : InputStream.nullInputStream();
            InputStream downstream;
            if (response.isSuccessful()) {
                downstream = convertStream(
                        request.getProtocol(), target.getProtocol(), upstream, response.code());
            } else {
                byte[] rawError = upstream.readAllBytes();
                upstream.close();
                String convertedError = ProtocolCodecs.convertError(
                        request.getProtocol(), target.getProtocol(), response.code(),
                        new String(rawError, StandardCharsets.UTF_8));
                downstream = new ByteArrayInputStream(convertedError.getBytes(StandardCharsets.UTF_8));
            }
            return StreamResult.builder()
                    .statusCode(response.code())
                    .body(downstream)
                    .cancel(call)
                    .build();
        } catch (UnsupportedProtocolFieldException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("流式转发失败: " + e.getMessage(), e);
        }
    }

    /**
     * 请求体共有加工：应用模型映射（字符串值改写，数组值不重采样）。
     * <p>模型映射是渠道级共有能力（所有厂商请求体的 model 字段同源语义）；
     * 子类如需厂商私有加工（如 OpenAI 族的 stream_options 注入），在 {@code super.prepareBody} 之上叠加。
     */
    protected JSONObject prepareBody(JSONObject body,
                                     ChannelConfig config,
                                     boolean stream,
                                     ProtocolFamily targetProtocol) {
        return applyModelMapping(body, config.getModelMapping());
    }

    /**
     * 应用模型映射：请求体中的 model 按下发名 → 上游真实名改写。
     * <p>例：
     * <pre>
     * mapping = {"gpt-4o": "deepseek-chat"}
     * 请求 model = gpt-4o  →  改写为 deepseek-chat
     * </pre>
     * <ul>
     *   <li>字符串值改写；</li>
     *   <li>数组值原样保留（采样由调用方在路由阶段完成并预烘焙）；</li>
     *   <li>映射为空或未命中时原样返回（不污染调用方入参）。</li>
     * </ul>
     */
    private static JSONObject applyModelMapping(JSONObject body, JSONObject mapping) {
        if (body == null || mapping == null || mapping.isEmpty()) {
            return body;
        }
        String model = body.getString("model");
        if (model == null) {
            return body;
        }
        Object mapped = mapping.get(model);
        if (mapped instanceof String s && !s.isEmpty()) {
            JSONObject copy = (JSONObject) body.clone();
            copy.put("model", s);
            return copy;
        }
        // 数组值：已在路由阶段采样预烘焙，此处不重采样
        return body;
    }

    /** 原生路径且 body 未被改写时复用原始请求字节。 */
    protected String serializeBody(ProxyRequest request, JSONObject preparedBody) {
        if (request.getRawBody() != null) {
            try {
                JSONObject original = JSON.parseObject(request.getRawBody());
                if (original != null) {
                    if (original.equals(preparedBody)) {
                        return request.getRawBody();
                    }
                    JSONObject sanitizedOriginal = MessageSanitizer.sanitizeMessages(
                            JSON.parseObject(original.toJSONString()));
                    if (sanitizedOriginal.equals(preparedBody)) {
                        return request.getRawBody();
                    }
                }
            } catch (Exception ignored) {
                // 非法原始 JSON 已在入站解析阶段拒绝。
            }
        }
        return preparedBody.toJSONString();
    }

    /** 目标协议响应转回入站协议；原生路径默认零转换。 */
    protected String convertResponse(ProtocolFamily inbound,
                                     ProtocolFamily target,
                                     String rawResponse) {
        if (inbound == target) {
            return rawResponse;
        }
        try {
            JSONObject wire = JSON.parseObject(rawResponse);
            return ProtocolCodecs.convertResponse(inbound, target, wire).toJSONString();
        } catch (UnsupportedProtocolFieldException e) {
            throw e;
        } catch (Exception e) {
            return rawResponse;
        }
    }

    /** 目标协议 SSE 转回入站协议；原生路径默认零转换。 */
    protected InputStream convertStream(ProtocolFamily inbound,
                                        ProtocolFamily target,
                                        InputStream upstream,
                                        int statusCode) {
        if (inbound == target || statusCode < 200 || statusCode >= 300) {
            return upstream;
        }
        return ProtocolCodecs.transformStream(upstream, inbound, target);
    }

    /** 连通性检查路径。 */
    protected String modelPath() {
        return "/v1/models";
    }

    protected NativeEndpoint primaryEndpoint() {
        List<NativeEndpoint> endpoints = nativeEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("适配器未声明原生端点: " + channelType());
        }
        return endpoints.get(0);
    }

    protected NativeEndpoint targetEndpoint(ProxyRequest request) {
        for (NativeEndpoint endpoint : nativeEndpoints()) {
            if (endpoint.getProtocol() == request.getProtocol()) {
                return endpoint;
            }
        }
        return primaryEndpoint();
    }

    protected String wirePath(ProxyRequest request, NativeEndpoint target) {
        if (target.getProtocol() != ProtocolFamily.CC) {
            return target.getPath();
        }
        if ("/v1/completions".equals(request.getInboundPath())) {
            return "/v1/completions";
        }
        if ("/v1/embeddings".equals(request.getInboundPath())) {
            return "/v1/embeddings";
        }
        return target.getPath();
    }

    private Request.Builder requestBuilder(ChannelConfig config,
                                           NativeEndpoint endpoint,
                                           String url) {
        Request.Builder builder = new Request.Builder().url(url);
        if (endpoint.getAuthStyle() == AuthStyle.X_API_KEY) {
            JSONObject extra = config.getExtra() == null ? new JSONObject() : config.getExtra();
            String version = extra.getString("anthropic_version");
            builder.header("x-api-key", config.getApiKey());
            builder.header("anthropic-version",
                    version == null || version.isBlank() ? DEFAULT_ANTHROPIC_VERSION : version);
        } else {
            builder.header("Authorization", "Bearer " + config.getApiKey());
        }
        return builder.header("Content-Type", "application/json");
    }

    protected TokenUsage extractUsage(ProtocolFamily inbound,
                                      ProtocolFamily target,
                                      JSONObject json) {
        JSONObject usage = json.getJSONObject("usage");
        if (usage == null) {
            return null;
        }
        long prompt = firstLong(usage, "prompt_tokens", "input_tokens");
        long completion = firstLong(usage, "completion_tokens", "output_tokens");
        long total = firstLong(usage, "total_tokens");
        if (total <= 0) {
            total = prompt + completion;
        }
        return TokenUsage.builder()
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(total)
                .build();
    }

    private static long firstLong(JSONObject json, String... keys) {
        for (String key : keys) {
            Long value = json.getLong(key);
            if (value != null) {
                return value;
            }
        }
        return 0L;
    }

    private static JSONObject parseOrRaw(String text) {
        try {
            return JSON.parseObject(text);
        } catch (Exception e) {
            JSONObject json = new JSONObject();
            json.put("raw", text);
            return json;
        }
    }

    protected static String joinUrl(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        String suffix = path == null ? "" : path;
        if (base.endsWith("/v1") && suffix.startsWith("/anthropic/v1/")) {
            base = base.substring(0, base.length() - 3);
        }
        if (base.endsWith("/v1") && suffix.startsWith("/v1/")) {
            suffix = suffix.substring(3);
        }
        return base + suffix;
    }

    private static String truncate(String value) {
        return value != null && value.length() > 200 ? value.substring(0, 200) : value;
    }
}
