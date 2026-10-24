package cn.qx.cases.proxy;

import cn.qx.api.dto.proxy.ProxyResult;
import cn.qx.api.IApiKeyCase;
import cn.qx.api.ISecurityAuditCase;
import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.domain.proxy.service.ISseUsageParserService;
import cn.qx.domain.proxy.support.MessageSanitizer;
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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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

    /** SSE usage 解析器提供器（原型 Bean） */
    @Resource
    private ObjectProvider<ISseUsageParserService> usageParserProvider;

    /** 解析请求并按 body.stream（缺省 Accept）确定响应形态。 */
    @Override
    public ProxyRequest prepare(String rawBody, String acceptHeader, String inboundPath) {
        String path = inboundPath == null || inboundPath.isBlank()
                ? ProxyRequest.DEFAULT_INBOUND_PATH
                : inboundPath;
        String auditedBody = securityAuditCase.auditRequest(rawBody, null, path);
        JSONObject body;
        try {
            body = JSON.parseObject(auditedBody);
        } catch (Exception e) {
            throw new ProxyException(400, "invalid_request_error", "invalid_request_error", "请求体不是合法 JSON");
        }
        if (body == null) {
            throw new ProxyException(400, "invalid_request_error", "invalid_request_error", "请求体不能为空");
        }
        String model = body.getString("model");
        if (model == null || model.isBlank()) {
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
                model, body, stream, path, InboundProtocol.fromPath(path).getFamily(), auditedBody);
    }

    /** 一次调用渠道门面并转换为字节中立结果。 */
    @Override
    public ProxyResult forward(ProxyRequest request, String apiKey) {
        ApiKey identity = authenticate(apiKey, request.getModel());
        DispatchOutcome outcome;
        try {
            outcome = dispatchInvoker.invoke(request);
        } catch (UnsupportedProtocolFieldException e) {
            throw new ProxyException(
                    400, "unsupported_field", "invalid_request_error", e.getMessage());
        }
        if (!outcome.isRouted()) {
            return ProxyResult.builder()
                    .statusCode(404)
                    .contentType(JSON_CONTENT_TYPE)
                    .raw(errorBody(outcome.getErrorMessage(), "invalid_request_error",
                            "model_not_found", request.getInboundPath()))
                    .build();
        }
        if (request.isStream() && outcome.getStreaming() != null) {
            StreamResult streamResult = outcome.getStreaming();
            InputStream body = streamResult.getBody();
            if (isSuccessful(streamResult.getStatusCode()) && body != null) {
                body = new UsageTrackingInputStream(
                        body, usageParserProvider.getObject(), total -> recordUsage(identity.getId(), total));
            }
            return ProxyResult.builder()
                    .statusCode(streamResult.getStatusCode())
                    .contentType(isSuccessful(streamResult.getStatusCode()) ? SSE_CONTENT_TYPE : JSON_CONTENT_TYPE)
                    .stream(body)
                    .cancel(streamResult.getCancel() == null ? null : streamResult.getCancel()::cancel)
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
        if (isSuccessful(forwardResult.getStatusCode()) && forwardResult.getUsage() != null) {
            recordUsage(identity.getId(), forwardResult.getUsage().getTotalTokens());
        }
        return ProxyResult.builder()
                .statusCode(forwardResult.getStatusCode())
                .contentType(JSON_CONTENT_TYPE)
                .raw(raw)
                .build();
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
        Runnable cancel = result.getCancel();
        if (cancel != null) {
            emitter.onCompletion(cancel);
            emitter.onError(error -> cancel.run());
        }
        boolean sent = false;
        try (InputStream input = result.getStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                emitter.send(Arrays.copyOf(buffer, read));
                sent = true;
            }
            emitter.complete();
        } catch (Exception e) {
            if (sent) {
                emitter.completeWithError(e);
            } else {
                response.setStatus(502);
                response.setContentType(JSON_CONTENT_TYPE);
                sendText(emitter,
                        errorBody("上游流式响应失败", "upstream_error", "upstream_error", inboundPath));
            }
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

    /**
     * 流式 usage tee：读取上游字节时旁路解析，EOF 后一次性记账；任何解析异常都静默忽略。
     */
    private static final class UsageTrackingInputStream extends FilterInputStream {

        private final ISseUsageParserService parser;
        private final java.util.function.LongConsumer recorder;
        private boolean recorded;

        private UsageTrackingInputStream(InputStream input,
                                         ISseUsageParserService parser,
                                         java.util.function.LongConsumer recorder) {
            super(input);
            this.parser = parser;
            this.recorder = recorder;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) {
                recordUsageAtEof();
            } else {
                parser.accept(new byte[]{(byte) value});
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read == -1) {
                recordUsageAtEof();
            } else if (read > 0) {
                parser.accept(Arrays.copyOfRange(buffer, offset, offset + read));
            }
            return read;
        }

        private void recordUsageAtEof() {
            if (recorded) {
                return;
            }
            recorded = true;
            parser.finish();
            parser.usageTokens().ifPresent(recorder::accept);
        }
    }
}
