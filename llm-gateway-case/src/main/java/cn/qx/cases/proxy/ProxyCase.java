package cn.qx.cases.proxy;

import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.proxy.model.valobj.ProxyResult;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import jakarta.servlet.http.HttpServletResponse;
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
public class ProxyCase {

    private static final String JSON_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
    private static final String SSE_CONTENT_TYPE = "text/event-stream;charset=UTF-8";

    @Resource
    private IDispatchInvoker dispatchInvoker;

    /** 解析请求并按 body.stream（缺省 Accept）确定响应形态。 */
    public ProxyRequest prepare(String rawBody, String acceptHeader) {
        JSONObject body;
        try {
            body = JSON.parseObject(rawBody);
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
        Boolean streamField = body.getBoolean("stream");
        boolean stream = streamField != null ? streamField : acceptsEventStream(acceptHeader);
        body.put("stream", stream);
        MessageSanitizer.sanitizeMessages(body);
        log.info("入站请求: model={}, stream={}, body={}", model, stream, body);
        return new ProxyRequest(model, body, stream);
    }

    /** 一次调用渠道门面并转换为字节中立结果。 */
    public ProxyResult forward(ProxyRequest request) {
        DispatchOutcome outcome = dispatchInvoker.invoke(request);
        if (!outcome.isRouted()) {
            return ProxyResult.builder()
                    .statusCode(404)
                    .contentType(JSON_CONTENT_TYPE)
                    .raw(errorBody(outcome.getErrorMessage(), "invalid_request_error", "model_not_found"))
                    .build();
        }
        if (request.isStream()) {
            StreamResult streamResult = outcome.getStreaming();
            return ProxyResult.builder()
                    .statusCode(streamResult.getStatusCode())
                    .contentType(isSuccessful(streamResult.getStatusCode()) ? SSE_CONTENT_TYPE : JSON_CONTENT_TYPE)
                    .stream(streamResult.getBody())
                    .cancel(streamResult.getCancel())
                    .build();
        }
        ForwardResult forwardResult = outcome.getUnary();
        if (forwardResult.getRaw() == null) {
            throw new IllegalStateException("适配器未返回原始响应文本");
        }
        String raw = forwardResult.getRaw();
        log.info("非流式响应: status={}, raw={}", forwardResult.getStatusCode(), raw);
        return ProxyResult.builder()
                .statusCode(forwardResult.getStatusCode())
                .contentType(JSON_CONTENT_TYPE)
                .raw(raw)
                .build();
    }

    /** 将结果写入 ResponseBodyEmitter；流式结果绑定取消句柄。 */
    public void write(ProxyResult result, ResponseBodyEmitter emitter, HttpServletResponse response) {
        response.setStatus(result.getStatusCode());
        response.setContentType(result.getContentType());
        if (result.getStream() == null) {
            sendText(emitter, result.getRaw());
            return;
        }
        Call cancel = result.getCancel();
        if (cancel != null) {
            emitter.onCompletion(cancel::cancel);
            emitter.onError(error -> cancel.cancel());
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
                sendText(emitter, errorBody("上游流式响应失败", "upstream_error", "upstream_error"));
            }
        }
    }

    /** 数据面 OpenAI 风格错误响应。 */
    public void writeError(ProxyException exception, ResponseBodyEmitter emitter, HttpServletResponse response) {
        response.setStatus(exception.getStatusCode());
        response.setContentType(JSON_CONTENT_TYPE);
        sendText(emitter, errorBody(exception.getInfo(), exception.getErrorType(), exception.getCode()));
    }

    private static void sendText(ResponseBodyEmitter emitter, String text) {
        try {
            emitter.send((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private static String errorBody(String message, String type, String code) {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", type);
        error.put("code", code);
        JSONObject body = new JSONObject();
        body.put("error", error);
        return body.toJSONString();
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
}
