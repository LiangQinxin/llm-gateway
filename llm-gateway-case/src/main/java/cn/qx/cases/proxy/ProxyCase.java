package cn.qx.cases.proxy;

import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.api.IApiKeyCase;
import cn.qx.api.IDataPlaneAuditCase;
import cn.qx.api.ISecurityAuditCase;
import cn.qx.api.dto.audit.DataPlaneLogData;
import cn.qx.api.dto.security.SecurityAuditOutcome;
import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.DispatchUnroutableReason;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.domain.proxy.support.MessageSanitizer;
import cn.qx.domain.proxy.support.StreamResponseCollector;
import cn.qx.domain.proxy.model.valobj.StreamAuditSnapshot;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.types.common.TimeUtils;
import cn.qx.types.enums.AuthFailureCode;
import cn.qx.types.exception.AppException;
import cn.qx.types.exception.UnsupportedProtocolFieldException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /v1/chat/completions 编排：解析、sanitize、分发与字节响应。
 */
@Slf4j
@Service
public class ProxyCase implements IProxyCase {

    /** 非流式响应 Content-Type：application/json */
    private static final String JSON_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
    /** 流式（SSE）响应 Content-Type：text/event-stream;charset=UTF-8 */
    private static final String SSE_CONTENT_TYPE = "text/event-stream;charset=UTF-8";

    /** 渠道分发调用端口 */
    @Resource
    private IDispatchInvoker dispatchInvoker;

    /** 调用方身份认证与配额用例 */
    @Resource
    private IApiKeyCase apiKeyCase;

    /** 请求侧安全审计用例 */
    @Resource
    private ISecurityAuditCase securityAuditCase;

    /** 数据面审计单写者 */
    @Resource
    private IDataPlaneAuditCase dataPlaneAuditCase;

    /** 流式审计收集器提供器（原型 Bean） */
    @Resource
    private ObjectProvider<StreamResponseCollector> streamCollectorProvider;

    /** 流式审计会话：仅 case 内部持有，API DTO 只暴露一次性 ID */
    private final Map<String, StreamAuditContext> streamAuditSessions = new ConcurrentHashMap<>();

    /** 解析请求并按 body.stream（缺省 Accept）确定响应形态。 */
    @Override
    public ProxyRequest prepare(String rawBody, String acceptHeader, String inboundPath) {
        return prepareInternal(rawBody, acceptHeader, inboundPath, null, false);
    }

    @Override
    public ProxyRequest prepare(String rawBody, String acceptHeader,
                                String inboundPath, String apiKey) {
        return prepareInternal(rawBody, acceptHeader, inboundPath, apiKey, true);
    }

    private ProxyRequest prepareInternal(String rawBody,
                                         String acceptHeader,
                                         String inboundPath,
                                         String apiKey,
                                         boolean authenticateFirst) {
        String startedAt = TimeUtils.now();
        String traceId = TimeUtils.uuid();
        String path = inboundPath == null || inboundPath.isBlank()
                ? ProxyRequest.DEFAULT_INBOUND_PATH
                : inboundPath;
        ApiKey identity = null;
        if (authenticateFirst) {
            try {
                identity = authenticate(apiKey, null);
            } catch (ProxyException e) {
                SecurityAuditOutcome unscanned = SecurityAuditOutcome.builder()
                        .auditBody(null)
                        .audit(AuditResult.clean(ScanPhase.REQUEST, "authentication rejected before pipeline"))
                        .blocked(false)
                        .build();
                ProxyRequest rejected = failureRequest(
                        resolveModel(rawBody), path, rawBody, unscanned,
                        startedAt, traceId, null);
                recordAudit(rejected, null, e.getStatusCode(),
                        errorBody(e.getInfo(), e.getErrorType(), e.getCode(), path),
                        null, null);
                throw e;
            }
        }
        SecurityAuditOutcome security = securityAuditCase.auditRequest(rawBody, null, path);
        String auditedBody = security.getForwardBody();
        if (security.isBlocked()) {
            ProxyRequest blocked = failureRequest(
                    resolveModel(rawBody), path, rawBody, security, startedAt, traceId, identity);
            String raw = errorBody(security.getAudit().getSummary(),
                    "invalid_request_error", "security_blocked", path);
            recordAudit(blocked, null, 403, raw, null, null);
            throw new ProxyException(403, "security_blocked",
                    "invalid_request_error", security.getAudit().getSummary());
        }
        JSONObject body;
        try {
            body = JSON.parseObject(auditedBody);
        } catch (Exception e) {
            ProxyRequest invalid = failureRequest(
                    "unknown", path, rawBody, security, startedAt, traceId, identity);
            String raw = errorBody("请求体不是合法 JSON", "invalid_request_error",
                    "invalid_request_error", path);
            recordAudit(invalid, null, 400, raw, null, null);
            throw new ProxyException(400, "invalid_request_error", "invalid_request_error", "请求体不是合法 JSON");
        }
        if (body == null) {
            ProxyRequest invalid = failureRequest(
                    "unknown", path, rawBody, security, startedAt, traceId, identity);
            String raw = errorBody("请求体不能为空", "invalid_request_error",
                    "invalid_request_error", path);
            recordAudit(invalid, null, 400, raw, null, null);
            throw new ProxyException(400, "invalid_request_error", "invalid_request_error", "请求体不能为空");
        }
        String model = body.getString("model");
        if (model == null || model.isBlank()) {
            ProxyRequest invalid = failureRequest(
                    "unknown", path, rawBody, security, startedAt, traceId, identity);
            String raw = errorBody("缺少 model", "invalid_request_error",
                    "invalid_request_error", path);
            recordAudit(invalid, null, 400, raw, null, null);
            throw new ProxyException(400, "invalid_request_error", "invalid_request_error", "缺少 model");
        }
        boolean streamCapable = !"/v1/embeddings".equals(path);
        Boolean streamField = body.getBoolean("stream");
        boolean stream = streamCapable
                && (streamField != null ? streamField : acceptsEventStream(acceptHeader));
        if (streamCapable && streamField == null && stream) {
            body.put("stream", true);
        }
        MessageSanitizer.sanitizeMessages(body);
        log.info("入站请求: model={}, stream={}, body={}", model, stream, body);
        return new ProxyRequest(
                model, body, stream, path, InboundProtocol.fromPath(path).getFamily(), auditedBody,
                startedAt, traceId, security.getAuditBody(),
                security.getAudit(),
                identity == null ? null : identity.getId(),
                identity == null ? null : identity.getName());
    }

    /** 一次调用渠道门面并转换为字节中立结果。 */
    @Override
    public ProxyResult forward(ProxyRequest request, String apiKey) {
        ApiKey identity;
        try {
            identity = authenticate(apiKey, request.getModel());
        } catch (ProxyException e) {
            recordAudit(request, null, e.getStatusCode(),
                    errorBody(e.getInfo(), e.getErrorType(), e.getCode(), request.getInboundPath()),
                    null, null);
            throw e;
        }
        request.setApiKeyId(identity.getId());
        request.setApiKeyName(identity.getName());
        DispatchOutcome outcome;
        try {
            outcome = dispatchInvoker.invoke(request);
        } catch (UnsupportedProtocolFieldException e) {
            ProxyException error = new ProxyException(
                    400, "unsupported_field", "invalid_request_error", e.getMessage());
            recordAudit(request, null, 400,
                    errorBody(e.getMessage(), "invalid_request_error", "unsupported_field",
                            request.getInboundPath()),
                    null, null);
            throw error;
        } catch (RuntimeException e) {
            recordAudit(request, null, 502,
                    errorBody("上游请求失败", "upstream_error", "upstream_error",
                            request.getInboundPath()),
                    null, null);
            throw e;
        }
        if (!outcome.isRouted()) {
            if (outcome.getReason() == DispatchUnroutableReason.NO_AVAILABLE_CHANNEL) {
                return ProxyResult.builder()
                        .statusCode(503)
                        .contentType(JSON_CONTENT_TYPE)
                        .raw(errorBody("当前没有可用渠道", "upstream_error",
                                "no_available_channel", request.getInboundPath()))
                        .build();
            }
            String raw = errorBody(outcome.getErrorMessage(), "invalid_request_error",
                    "model_not_found", request.getInboundPath());
            recordAudit(request, outcome, 404, raw, null, null);
            return ProxyResult.builder()
                    .statusCode(404)
                    .contentType(JSON_CONTENT_TYPE)
                    .raw(raw)
                    .build();
        }
        if (request.isStream() && outcome.getStreaming() != null) {
            StreamResult streamResult = outcome.getStreaming();
            String auditSessionId = TimeUtils.uuid();
            streamAuditSessions.put(auditSessionId, new StreamAuditContext(
                    request, outcome, streamCollectorProvider.getObject()));
            return ProxyResult.builder()
                    .statusCode(streamResult.getStatusCode())
                    .contentType(isSuccessful(streamResult.getStatusCode()) ? SSE_CONTENT_TYPE : JSON_CONTENT_TYPE)
                    .stream(streamResult.getBody())
                    .cancel(streamResult.getCancel() == null ? null : streamResult.getCancel()::cancel)
                    .auditSessionId(auditSessionId)
                    .build();
        }
        ForwardResult forwardResult = outcome.getUnary();
        if (forwardResult == null) {
            throw new IllegalStateException("分发结果既无流式响应也无非流式响应");
        }
        if (forwardResult.getRaw() == null) {
            throw new IllegalStateException("适配器未返回原始响应文本");
        }
        String raw = forwardResult.getRaw();
        log.info("非流式响应: status={}, raw={}", forwardResult.getStatusCode(), raw);
        recordUnaryAudit(request, outcome, forwardResult);
        if (isSuccessful(forwardResult.getStatusCode()) && forwardResult.getUsage() != null) {
            recordUsage(identity.getId(), forwardResult.getUsage().getTotalTokens());
        }
        return ProxyResult.builder()
                .statusCode(forwardResult.getStatusCode())
                .contentType(JSON_CONTENT_TYPE)
                .raw(raw)
                .build();
    }

    private void recordUnaryAudit(ProxyRequest request,
                                  DispatchOutcome outcome,
                                  ForwardResult result) {
        if (result == null) {
            return;
        }
        String responseChoices = isSuccessful(result.getStatusCode())
                && result.getBody() != null
                && result.getBody().getJSONArray("choices") != null
                ? result.getBody().getJSONArray("choices").toJSONString()
                : null;
        recordAudit(request, outcome, result.getStatusCode(), result.getRaw(),
                result.getUsage(), responseChoices);
    }

    private void recordAudit(ProxyRequest request,
                             DispatchOutcome outcome,
                             int statusCode,
                             String raw,
                             cn.qx.domain.channel.model.valobj.TokenUsage usage,
                             String responseChoices) {
        AuditResult audit = request.getAuditResult() == null
                ? AuditResult.clean(ScanPhase.REQUEST, "clean")
                : request.getAuditResult();
        DataPlaneLogData.DataPlaneLogDataBuilder builder = DataPlaneLogData.builder()
                .apiKeyId(request.getApiKeyId())
                .apiKeyName(request.getApiKeyName())
                .channelId(outcome == null ? null : outcome.getChannelId())
                .channelName(outcome == null ? null : outcome.getChannelName())
                .model(request.getModel())
                .upstreamModel(outcome == null ? null : outcome.getUpstreamModel())
                .mode(modeOf(request.getInboundPath()))
                .protocolType(request.isStream() ? "sse" : "http")
                .stream(request.isStream() ? 1 : 0)
                .isRetry(outcome != null && outcome.getAttemptCount() > 1 ? 1 : 0)
                .statusCode(statusCode)
                .durationMs(TimeUtils.elapsedMillis(request.getStartedAt()))
                .riskLevel(levelName(audit))
                .riskScore(audit.getScore())
                .riskSummary(audit.getSummary())
                .securityAction(actionName(audit.getFinalAction()))
                .sanitized(audit.isSanitized() ? 1 : 0)
                .blockedReason(audit.isBlocked() ? audit.getSummary() : null)
                .requestBody(request.getAuditBody())
                .responseChoices(responseChoices)
                .traceId(request.getTraceId());
        if (usage != null) {
            builder.promptTokens(usage.getPromptTokens())
                    .completionTokens(usage.getCompletionTokens())
                    .totalTokens(usage.getTotalTokens());
        }
        if (!isSuccessful(statusCode)) {
            builder.errorMessage(outcome != null && outcome.getErrorMessage() != null
                    ? outcome.getErrorMessage() : raw);
        }
        dataPlaneAuditCase.recordDataPlane(builder.build(), audit.getFindings());
    }

    private static ProxyRequest failureRequest(String model,
                                               String path,
                                               String rawBody,
                                               SecurityAuditOutcome security,
                                               String startedAt,
                                               String traceId,
                                               ApiKey identity) {
        return ProxyRequest.builder()
                .model(model)
                .body(new JSONObject())
                .stream(false)
                .inboundPath(path)
                .protocol(InboundProtocol.fromPath(path).getFamily())
                .rawBody(rawBody)
                .startedAt(startedAt)
                .traceId(traceId)
                .auditBody(security.getAuditBody())
                .auditResult(security.getAudit())
                .apiKeyId(identity == null ? null : identity.getId())
                .apiKeyName(identity == null ? null : identity.getName())
                .build();
    }

    private static String resolveModel(String rawBody) {
        try {
            JSONObject parsed = JSON.parseObject(rawBody);
            String model = parsed == null ? null : parsed.getString("model");
            return model == null || model.isBlank() ? "unknown" : model;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String levelName(AuditResult result) {
        if (result == null || result.getLevel() == null) {
            return "Clean";
        }
        String value = result.getLevel().name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String actionName(SecurityAction action) {
        if (action == null) {
            return "Allow";
        }
        return switch (action) {
            case SANITIZE -> "Sanitize";
            case BLOCK -> "Block";
            default -> "Allow";
        };
    }

    private static String modeOf(String inboundPath) {
        if (inboundPath == null) {
            return "chat";
        }
        if (inboundPath.contains("embedding")) {
            return "embedding";
        }
        if (inboundPath.contains("completion") || inboundPath.contains("responses")) {
            return "completion";
        }
        return "chat";
    }

    private ApiKey authenticate(String apiKey, String model) {
        try {
            return apiKeyCase.authenticate(apiKey, model, null);
        } catch (AppException e) {
            switch (AuthFailureCode.fromCode(e.getCode())) {
                case MODEL_NOT_FOUND -> throw new ProxyException(
                        404, "model_not_found", "invalid_request_error",
                        "The model '" + model + "' does not exist or you don't have access to it");
                case INSUFFICIENT_QUOTA -> throw new ProxyException(
                        403, "insufficient_quota", "insufficient_quota", "调用方配额已用尽");
                case FORBIDDEN -> throw new ProxyException(
                        403, "forbidden", "invalid_request_error", "调用方无权访问该资源");
                case INVALID_API_KEY -> throw new ProxyException(
                        401, "invalid_api_key", "invalid_request_error", "调用方 API Key 无效");
            }
            throw new IllegalStateException("未知认证失败码: " + e.getCode());
        }
    }

    private void recordUsage(String apiKeyId, long totalTokens) {
        if (totalTokens <= 0) {
            return;
        }
        try {
            apiKeyCase.recordUsage(apiKeyId, totalTokens);
            log.info("配额已记账: apiKeyId={}, totalTokens={}", apiKeyId, totalTokens);
        } catch (Exception e) {
            log.warn("配额记账失败（best-effort）: apiKeyId={}, totalTokens={}, error={}",
                    apiKeyId, totalTokens, e.getMessage());
        }
    }

    /** 将结果写入 ResponseBodyEmitter；流式结果绑定取消句柄。 */
    @Override
    public void write(ProxyResult result, String inboundPath,
                      ResponseBodyEmitter emitter, HttpServletResponse response) {
        response.setStatus(result.getStatusCode());
        response.setContentType(result.getContentType());
        if (result.getStream() == null) {
            sendText(emitter, result.getRaw());
            return;
        }
        StreamAuditContext auditContext = result.getAuditSessionId() == null
                ? null : streamAuditSessions.get(result.getAuditSessionId());
        Runnable cancel = result.getCancel();
        AtomicBoolean sent = new AtomicBoolean(false);
        AtomicBoolean auditSettled = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            recordStreamAuditOnce(
                    result, auditContext, auditSettled, result.getStatusCode(),
                    result.getStatusCode() >= 300 ? "upstream_stream_error" : null, true);
            if (cancel != null) {
                cancel.run();
            }
        });
        emitter.onError(error -> {
            recordStreamAuditOnce(result, auditContext, auditSettled,
                    sent.get() ? 499 : 502,
                    sent.get() ? "client_cancelled" : "upstream_stream_error", false);
            if (cancel != null) {
                cancel.run();
            }
        });
        try (InputStream input = result.getStream()) {
            byte[] buffer = new byte[8192];
            while (true) {
                int read;
                try {
                    read = input.read(buffer);
                } catch (Exception upstreamError) {
                    recordStreamAuditOnce(result, auditContext, auditSettled,
                            502, "upstream_stream_error", false);
                    if (!sent.get()) {
                        response.setStatus(502);
                        response.setContentType(JSON_CONTENT_TYPE);
                        sendText(emitter, errorBody("上游流式响应失败",
                                "upstream_error", "upstream_error", inboundPath));
                    } else {
                        emitter.completeWithError(upstreamError);
                    }
                    return;
                }
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                byte[] chunk = Arrays.copyOf(buffer, read);
                if (auditContext != null) {
                    auditContext.collector.accept(chunk);
                }
                try {
                    emitter.send(chunk);
                } catch (Exception clientError) {
                    recordStreamAuditOnce(result, auditContext, auditSettled,
                            sent.get() ? 499 : 502,
                            sent.get() ? "client_cancelled" : "upstream_stream_error", false);
                    emitter.completeWithError(clientError);
                    return;
                }
                sent.set(true);
            }
            emitter.complete();
        } catch (Exception e) {
            recordStreamAuditOnce(result, auditContext, auditSettled,
                    sent.get() ? 499 : 502,
                    sent.get() ? "client_cancelled" : "upstream_stream_error", false);
            emitter.completeWithError(e);
        }
    }

    private void recordStreamAuditOnce(ProxyResult result,
                                       StreamAuditContext auditContext,
                                       AtomicBoolean auditSettled,
                                       int statusCode,
                                       String errorMessage,
                                       boolean finishCollector) {
        if (!auditSettled.compareAndSet(false, true)) {
            return;
        }
        StreamAuditSnapshot snapshot = null;
        if (auditContext != null) {
            snapshot = finishCollector
                    ? auditContext.collector.finish()
                    : auditContext.collector.snapshot();
        }
        String responseChoices = snapshot != null
                && (statusCode == 499 || isSuccessful(statusCode))
                ? snapshot.getResponseChoices() : null;
        if (auditContext != null) {
            recordAudit(auditContext.request, auditContext.outcome, statusCode, errorMessage,
                    snapshot == null ? null : snapshot.getUsage(), responseChoices);
            if (isSuccessful(statusCode) && snapshot != null && snapshot.getUsage() != null) {
                recordUsage(auditContext.request.getApiKeyId(),
                        snapshot.getUsage().getTotalTokens());
            }
        }
        if (result.getAuditSessionId() != null) {
            streamAuditSessions.remove(result.getAuditSessionId());
        }
    }

    /** 数据面 OpenAI 风格错误响应。 */
    @Override
    public void writeError(ProxyException exception, String inboundPath,
                           ResponseBodyEmitter emitter, HttpServletResponse response) {
        response.setStatus(exception.getStatusCode());
        response.setContentType(JSON_CONTENT_TYPE);
        sendText(emitter, errorBody(
                exception.getInfo(), exception.getErrorType(), exception.getCode(), inboundPath));
    }

    /** 尚未发送任何字节时的系统失败，回退为 OpenAI 风格 JSON。 */
    @Override
    public void writeUpstreamFailure(ResponseBodyEmitter emitter,
                                     String inboundPath,
                                     HttpServletResponse response,
                                     Throwable error) {
        log.warn("数据面上游失败，回退 JSON: {}", error.getMessage());
        response.setStatus(502);
        response.setContentType(JSON_CONTENT_TYPE);
        sendText(emitter,
                errorBody("上游请求失败", "upstream_error", "upstream_error", inboundPath));
    }

    @Override
    public void recordRejected(String inboundPath, int statusCode, String code,
                               String message, String clientIp) {
        dataPlaneAuditCase.recordDataPlane(DataPlaneLogData.builder()
                .model("unknown")
                .mode(modeOf(inboundPath))
                .protocolType("http")
                .stream(0)
                .isRetry(0)
                .statusCode(statusCode)
                .clientIp(clientIp)
                .errorMessage(code + ": " + message)
                .traceId(TimeUtils.uuid())
                .build(), List.of());
    }

    private static void sendText(ResponseBodyEmitter emitter, String text) {
        try {
            emitter.send((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private static String errorBody(String message, String type, String code, String inboundPath) {
        if (InboundProtocol.fromPath(inboundPath).isAnthropicError()) {
            JSONObject error = new JSONObject();
            error.put("type", anthropicErrorType(code));
            error.put("message", message);
            error.put("code", code);
            JSONObject body = new JSONObject();
            body.put("type", "error");
            body.put("error", error);
            return body.toJSONString();
        }
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", type);
        error.put("code", code);
        JSONObject body = new JSONObject();
        body.put("error", error);
        return body.toJSONString();
    }

    private static String anthropicErrorType(String code) {
        return switch (code == null ? "" : code) {
            case "invalid_api_key" -> "authentication_error";
            case "model_not_found" -> "not_found_error";
            case "forbidden", "insufficient_quota" -> "permission_error";
            default -> "invalid_request_error";
        };
    }

    private static boolean acceptsEventStream(String acceptHeader) {
        if (acceptHeader == null || acceptHeader.isBlank()) {
            return false;
        }
        try {
            List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
            for (MediaType mediaType : mediaTypes) {
                if (mediaType.isCompatibleWith(MediaType.TEXT_EVENT_STREAM)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    /** 流式审计会话上下文。 */
    private record StreamAuditContext(ProxyRequest request,
                                      DispatchOutcome outcome,
                                      StreamResponseCollector collector) {
    }

}
