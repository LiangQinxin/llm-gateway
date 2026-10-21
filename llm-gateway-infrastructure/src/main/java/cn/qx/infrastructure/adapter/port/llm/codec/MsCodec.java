package cn.qx.infrastructure.adapter.port.llm.codec;

import cn.qx.types.exception.UnsupportedProtocolFieldException;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Set;

/**
 * Anthropic Messages(ms) ↔ canonical(cc) 双向 codec。
 */
public final class MsCodec {

    private static final Set<String> MS_REQUEST_FIELDS = Set.of(
            "model", "system", "messages", "max_tokens", "stream",
            "temperature", "top_p", "stop_sequences", "tools", "tool_choice", "metadata");
    private static final Set<String> CC_REQUEST_FIELDS = Set.of(
            "model", "messages", "max_tokens", "stream", "temperature", "top_p",
            "stop", "tools", "tool_choice", "functions", "function_call",
            "stream_options", "metadata");
    private static final Set<String> MS_RESPONSE_FIELDS = Set.of(
            "id", "type", "role", "model", "content", "stop_reason", "usage");
    private static final Set<String> CC_RESPONSE_FIELDS = Set.of(
            "id", "model", "choices", "usage", "object", "created");
    private static final Set<String> MS_MESSAGE_FIELDS = Set.of("role", "content");
    private static final Set<String> CC_MESSAGE_FIELDS = Set.of(
            "role", "content", "tool_calls", "function_call");
    private static final Set<String> TEXT_BLOCK_FIELDS = Set.of("type", "text", "annotations");
    private static final Set<String> MS_UNSUPPORTED_FUNCTIONAL_FIELDS = Set.of(
            "thinking", "top_k", "mcp_servers", "context_management");
    private static final Set<String> CC_UNSUPPORTED_BY_MS_FIELDS = Set.of(
            "n", "presence_penalty", "frequency_penalty", "logit_bias", "logprobs",
            "top_logprobs", "response_format", "seed", "parallel_tool_calls",
            "reasoning_effort", "max_completion_tokens", "store", "modalities",
            "audio", "prediction", "user", "web_search_options", "stream_options");

    private MsCodec() {
    }

    /** ms 请求 → canonical 请求。 */
    public static JSONObject toCanonicalRequest(JSONObject request) {
        rejectFunctionalFields(request, MS_UNSUPPORTED_FUNCTIONAL_FIELDS);
        JSONObject canonical = new JSONObject();
        copy(request, canonical, "model", "stream", "temperature", "top_p", "metadata");
        canonical.put("max_tokens", request.getLongValue("max_tokens", 4096L));
        if (request.containsKey("tools") || request.containsKey("tool_choice")) {
            throw new UnsupportedProtocolFieldException("tools");
        }
        JSONArray messages = new JSONArray();
        if (request.containsKey("system")) {
            messages.add(message("system", text(request.get("system"))));
        }
        JSONArray sourceMessages = request.getJSONArray("messages");
        if (sourceMessages != null) {
            for (int i = 0; i < sourceMessages.size(); i++) {
                JSONObject source = sourceMessages.getJSONObject(i);
                rejectUnsupportedFields(source, MS_MESSAGE_FIELDS, "messages.");
                messages.add(message(source.getString("role"), text(source.get("content"))));
            }
        }
        canonical.put("messages", messages);
        copy(request, canonical, "stop_sequences");
        if (canonical.containsKey("stop_sequences")) {
            canonical.put("stop", canonical.remove("stop_sequences"));
        }
        copyUnknown(request, canonical, MS_REQUEST_FIELDS);
        return canonical;
    }

    /** canonical 请求 → ms 请求。 */
    public static JSONObject fromCanonicalRequest(JSONObject canonical) {
        rejectFunctionalFields(canonical, CC_UNSUPPORTED_BY_MS_FIELDS);
        ensureNoTools(canonical);
        JSONObject request = new JSONObject();
        copy(canonical, request, "model", "stream", "temperature", "top_p", "metadata");
        if (canonical.containsKey("max_tokens")) {
            request.put("max_tokens", canonical.get("max_tokens"));
        } else {
            request.put("max_tokens", 4096L);
        }
        if (canonical.containsKey("stop")) {
            request.put("stop_sequences", canonical.get("stop"));
        }
        JSONArray system = new JSONArray();
        JSONArray messages = new JSONArray();
        JSONArray source = canonical.getJSONArray("messages");
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                JSONObject item = source.getJSONObject(i);
                String role = item.getString("role");
                if (item.containsKey("tool_calls") || item.containsKey("function_call")) {
                    throw new UnsupportedProtocolFieldException("tools");
                }
                rejectUnsupportedFields(item, CC_MESSAGE_FIELDS, "messages.");
                if ("system".equals(role)) {
                    system.add(text(item.get("content")));
                } else {
                    messages.add(message(role, text(item.get("content"))));
                }
            }
        }
        if (!system.isEmpty()) {
            request.put("system", String.join("\n", system.toJavaList(String.class)));
        }
        request.put("messages", messages);
        copyUnknown(canonical, request, CC_REQUEST_FIELDS);
        return request;
    }

    /** ms 响应 → canonical 响应。 */
    public static JSONObject toCanonicalResponse(JSONObject response) {
        JSONObject canonical = new JSONObject();
        canonical.put("id", response.getString("id"));
        canonical.put("model", response.getString("model"));
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", text(response.get("content")));
        choice.put("message", message);
        choice.put("finish_reason", finishReason(response.getString("stop_reason")));
        choices.add(choice);
        canonical.put("choices", choices);
        canonical.put("usage", usageFromAnthropic(response.getJSONObject("usage")));
        copyUnknown(response, canonical, MS_RESPONSE_FIELDS);
        return canonical;
    }

    /** canonical 响应 → ms 响应。 */
    public static JSONObject fromCanonicalResponse(JSONObject canonical) {
        JSONObject response = new JSONObject();
        response.put("id", canonical.getString("id"));
        response.put("type", "message");
        response.put("role", "assistant");
        response.put("model", canonical.getString("model"));
        JSONArray content = new JSONArray();
        JSONObject textBlock = new JSONObject();
        textBlock.put("type", "text");
        textBlock.put("text", completionText(canonical));
        content.add(textBlock);
        response.put("content", content);
        response.put("stop_reason", stopReason(firstFinishReason(canonical)));
        response.put("usage", usageToAnthropic(canonical.getJSONObject("usage")));
        copyUnknown(canonical, response, CC_RESPONSE_FIELDS);
        return response;
    }

    private static String completionText(JSONObject canonical) {
        JSONArray choices = canonical.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message == null ? "" : text(message.get("content"));
    }

    private static String firstFinishReason(JSONObject canonical) {
        JSONArray choices = canonical.getJSONArray("choices");
        return choices == null || choices.isEmpty()
                ? null
                : choices.getJSONObject(0).getString("finish_reason");
    }

    private static JSONObject usageFromAnthropic(JSONObject usage) {
        JSONObject canonical = new JSONObject();
        if (usage == null) {
            return canonical;
        }
        long input = usage.getLongValue("input_tokens");
        long output = usage.getLongValue("output_tokens");
        canonical.put("prompt_tokens", input);
        canonical.put("completion_tokens", output);
        canonical.put("total_tokens", input + output);
        return canonical;
    }

    private static JSONObject usageToAnthropic(JSONObject usage) {
        JSONObject result = new JSONObject();
        if (usage == null) {
            return result;
        }
        result.put("input_tokens", usage.getLongValue("prompt_tokens"));
        result.put("output_tokens", usage.getLongValue("completion_tokens"));
        return result;
    }

    private static String finishReason(String stopReason) {
        return switch (stopReason == null ? "" : stopReason) {
            case "max_tokens" -> "length";
            case "tool_use" -> "tool_calls";
            default -> "stop";
        };
    }

    private static String stopReason(String finishReason) {
        return switch (finishReason == null ? "" : finishReason) {
            case "length" -> "max_tokens";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }

    static String text(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String value) {
            return value;
        }
        if (content instanceof JSONArray blocks) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < blocks.size(); i++) {
                Object item = blocks.get(i);
                if (!(item instanceof JSONObject block)) {
                    throw new UnsupportedProtocolFieldException("content");
                }
                String type = block.getString("type");
                if (!"text".equals(type) && !"input_text".equals(type) && !"output_text".equals(type)) {
                    throw new UnsupportedProtocolFieldException("content." + type);
                }
                rejectUnsupportedFields(block, TEXT_BLOCK_FIELDS, "content.");
                Object annotations = block.get("annotations");
                if (annotations != null
                        && (!(annotations instanceof JSONArray values) || !values.isEmpty())) {
                    throw new UnsupportedProtocolFieldException("content.annotations");
                }
                text.append(block.getString("text"));
            }
            return text.toString();
        }
        throw new UnsupportedProtocolFieldException("content");
    }

    static JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    static void ensureNoTools(JSONObject body) {
        if (body.containsKey("tools") || body.containsKey("tool_choice")
                || body.containsKey("functions") || body.containsKey("function_call")) {
            throw new UnsupportedProtocolFieldException("tools");
        }
    }

    static void rejectFunctionalFields(JSONObject body, Set<String> unsupportedFields) {
        for (String field : unsupportedFields) {
            if (body.containsKey(field)) {
                throw new UnsupportedProtocolFieldException(field);
            }
        }
    }

    static void rejectUnsupportedFields(JSONObject source,
                                        Set<String> supportedFields,
                                        String prefix) {
        for (String field : source.keySet()) {
            if (!supportedFields.contains(field)) {
                throw new UnsupportedProtocolFieldException(prefix + field);
            }
        }
    }

    static void copy(JSONObject source, JSONObject target, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    static void copyUnknown(JSONObject source, JSONObject target, Set<String> known) {
        for (String key : source.keySet()) {
            if (!known.contains(key) && !target.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }
}
