package cn.qx.infrastructure.adapter.port.llm.codec;

import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.InputStream;

/**
 * 以 canonical(cc) 为中转的协议 codec 路由。
 */
public final class ProtocolCodecs {

    private ProtocolCodecs() {
    }

    /** 入站请求 → 目标协议请求。 */
    public static JSONObject convertRequest(ProtocolFamily inbound,
                                            ProtocolFamily target,
                                            JSONObject body) {
        if (inbound == target) {
            return body;
        }
        return fromCanonicalRequest(target, toCanonicalRequest(inbound, body));
    }

    /** 目标协议响应 → 入站协议响应。 */
    public static JSONObject convertResponse(ProtocolFamily inbound,
                                             ProtocolFamily target,
                                             JSONObject body) {
        if (inbound == target) {
            return body;
        }
        return fromCanonicalResponse(inbound, toCanonicalResponse(target, body));
    }

    /** 上游错误体 → 入站协议错误体。 */
    public static String convertError(ProtocolFamily inbound,
                                      ProtocolFamily target,
                                      int status,
                                      String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (inbound == target) {
            try {
                if (JSON.parseObject(raw).getJSONObject("error") != null) {
                    return raw;
                }
            } catch (Exception ignored) {
                // 下方按入站协议生成标准错误体。
            }
        }
        try {
            JSONObject source = JSON.parseObject(raw);
            JSONObject sourceError = source.getJSONObject("error");
            String message = sourceError == null
                    ? source.getString("message")
                    : sourceError.getString("message");
            if (message == null) {
                message = sourceError == null ? "上游请求失败" : sourceError.toJSONString();
            }
            if (inbound == ProtocolFamily.MS) {
                JSONObject error = new JSONObject();
                error.put("type", anthropicErrorType(status));
                error.put("message", message);
                JSONObject body = new JSONObject();
                body.put("type", "error");
                body.put("error", error);
                return body.toJSONString();
            }
            JSONObject error = new JSONObject();
            error.put("message", message);
            error.put("type", "invalid_request_error");
            error.put("code", openAiErrorCode(status));
            JSONObject body = new JSONObject();
            body.put("error", error);
            return body.toJSONString();
        } catch (Exception e) {
            if (inbound == ProtocolFamily.MS) {
                JSONObject error = new JSONObject();
                error.put("type", anthropicErrorType(status));
                error.put("message", raw);
                error.put("code", openAiErrorCode(status));
                JSONObject body = new JSONObject();
                body.put("type", "error");
                body.put("error", error);
                return body.toJSONString();
            }
            JSONObject error = new JSONObject();
            error.put("message", raw);
            error.put("type", "invalid_request_error");
            error.put("code", openAiErrorCode(status));
            JSONObject body = new JSONObject();
            body.put("error", error);
            return body.toJSONString();
        }
    }

    /** 目标协议 SSE → 入站协议 SSE。 */
    public static InputStream transformStream(InputStream upstream,
                                              ProtocolFamily inbound,
                                              ProtocolFamily upstreamProtocol) {
        if (inbound == upstreamProtocol) {
            return upstream;
        }
        return new SseProtocolTransformer(upstream, upstreamProtocol, inbound);
    }

    private static JSONObject toCanonicalRequest(ProtocolFamily family, JSONObject body) {
        return switch (family) {
            case CC -> body;
            case MS -> MsCodec.toCanonicalRequest(body);
            case RP -> RpCodec.toCanonicalRequest(body);
        };
    }

    private static JSONObject fromCanonicalRequest(ProtocolFamily family, JSONObject canonical) {
        return switch (family) {
            case CC -> canonical;
            case MS -> MsCodec.fromCanonicalRequest(canonical);
            case RP -> RpCodec.fromCanonicalRequest(canonical);
        };
    }

    private static JSONObject toCanonicalResponse(ProtocolFamily family, JSONObject body) {
        return switch (family) {
            case CC -> body;
            case MS -> MsCodec.toCanonicalResponse(body);
            case RP -> RpCodec.toCanonicalResponse(body);
        };
    }

    private static JSONObject fromCanonicalResponse(ProtocolFamily family, JSONObject canonical) {
        return switch (family) {
            case CC -> canonical;
            case MS -> MsCodec.fromCanonicalResponse(canonical);
            case RP -> RpCodec.fromCanonicalResponse(canonical);
        };
    }

    private static String anthropicErrorType(int status) {
        return switch (status) {
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            default -> "invalid_request_error";
        };
    }

    private static String openAiErrorCode(int status) {
        return switch (status) {
            case 401 -> "invalid_api_key";
            case 403 -> "insufficient_quota";
            case 404 -> "model_not_found";
            default -> "invalid_request_error";
        };
    }
}
