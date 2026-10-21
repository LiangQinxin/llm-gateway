package cn.qx.infrastructure.adapter.port.llm.codec;

import cn.qx.types.exception.UnsupportedProtocolFieldException;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Set;

/**
 * OpenAI Responses(rp) ↔ canonical(cc) 双向 codec。
 */
public final class RpCodec {

    private static final Set<String> RP_REQUEST_FIELDS = Set.of(
            "model", "instructions", "input", "max_output_tokens", "stream",
            "temperature", "top_p", "tools", "tool_choice", "metadata");
    private static final Set<String> CC_REQUEST_FIELDS = Set.of(
            "model", "messages", "max_tokens", "stream", "temperature", "top_p",
            "stop", "tools", "tool_choice", "functions", "function_call",
            "stream_options", "metadata");
    private static final Set<String> RP_RESPONSE_FIELDS = Set.of(
            "id", "object", "model", "output", "usage", "status", "created_at");
    private static final Set<String> CC_RESPONSE_FIELDS = Set.of(
            "id", "model", "choices", "usage", "object", "created");
    private static final Set<String> RP_INPUT_MESSAGE_FIELDS = Set.of(
            "type", "role", "content");
    private static final Set<String> CC_MESSAGE_FIELDS = Set.of(
            "role", "content", "tool_calls", "function_call");
    private static final Set<String> RP_UNSUPPORTED_BY_CC_FIELDS = Set.of(
            "background", "conversation", "include", "max_tool_calls", "previous_response_id",
            "prompt", "reasoning", "store", "text", "truncation");
    private static final Set<String> CC_UNSUPPORTED_BY_RP_FIELDS = Set.of(
            "n", "presence_penalty", "frequency_penalty", "logit_bias", "logprobs",
            "top_logprobs", "response_format", "seed", "parallel_tool_calls",
            "max_completion_tokens", "modalities", "audio", "prediction", "user",
            "web_search_options");

    private RpCodec() {
    }

    /** rp 请求 → canonical 请求。 */
    public static JSONObject toCanonicalRequest(JSONObject request) {
        MsCodec.rejectFunctionalFields(request, RP_UNSUPPORTED_BY_CC_FIELDS);
        MsCodec.ensureNoTools(request);
        JSONObject canonical = new JSONObject();
        MsCodec.copy(request, canonical, "model", "stream", "temperature", "top_p", "metadata");
        if (request.containsKey("max_output_tokens")) {
            canonical.put("max_tokens", request.get("max_output_tokens"));
        }
        JSONArray messages = new JSONArray();
        if (request.containsKey("instructions")) {
            messages.add(MsCodec.message("system", MsCodec.text(request.get("instructions"))));
        }
        Object input = request.get("input");
        if (input instanceof String value) {
            messages.add(MsCodec.message("user", value));
        } else if (input instanceof JSONArray items) {
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                if (item instanceof String value) {
                    messages.add(MsCodec.message("user", value));
                } else if (item instanceof JSONObject message) {
                    String itemType = message.getString("type");
                    if (itemType != null && !"message".equals(itemType)) {
                        throw new UnsupportedProtocolFieldException("input." + itemType);
                    }
                    MsCodec.rejectUnsupportedFields(
                            message, RP_INPUT_MESSAGE_FIELDS, "input.");
                    messages.add(MsCodec.message(
                            message.getString("role"), MsCodec.text(message.get("content"))));
                }
            }
        }
        canonical.put("messages", messages);
        MsCodec.copyUnknown(request, canonical, RP_REQUEST_FIELDS);
        return canonical;
    }

    /** canonical 请求 → rp 请求。 */
    public static JSONObject fromCanonicalRequest(JSONObject canonical) {
        MsCodec.rejectFunctionalFields(canonical, CC_UNSUPPORTED_BY_RP_FIELDS);
        MsCodec.ensureNoTools(canonical);
        JSONObject request = new JSONObject();
        MsCodec.copy(canonical, request, "model", "stream", "temperature", "top_p", "metadata");
        if (canonical.containsKey("max_tokens")) {
            request.put("max_output_tokens", canonical.get("max_tokens"));
        }
        JSONArray input = new JSONArray();
        JSONArray messages = canonical.getJSONArray("messages");
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                JSONObject message = messages.getJSONObject(i);
                if (message.containsKey("tool_calls") || message.containsKey("function_call")) {
                    throw new UnsupportedProtocolFieldException("tools");
                }
                MsCodec.rejectUnsupportedFields(
                        message, CC_MESSAGE_FIELDS, "messages.");
                if ("system".equals(message.getString("role"))) {
                    request.put("instructions", MsCodec.text(message.get("content")));
                } else {
                    JSONObject item = new JSONObject();
                    item.put("role", message.getString("role"));
                    item.put("content", MsCodec.text(message.get("content")));
                    input.add(item);
                }
            }
        }
        request.put("input", input);
        MsCodec.copyUnknown(canonical, request, CC_REQUEST_FIELDS);
        return request;
    }

    /** rp 响应 → canonical 响应。 */
    public static JSONObject toCanonicalResponse(JSONObject response) {
        JSONObject canonical = new JSONObject();
        canonical.put("id", response.getString("id"));
        canonical.put("model", response.getString("model"));
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", outputText(response.getJSONArray("output")));
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);
        canonical.put("choices", choices);
        JSONObject usage = response.getJSONObject("usage");
        if (usage != null) {
            long input = usage.getLongValue("input_tokens");
            long output = usage.getLongValue("output_tokens");
            JSONObject canonicalUsage = new JSONObject();
            canonicalUsage.put("prompt_tokens", input);
            canonicalUsage.put("completion_tokens", output);
            canonicalUsage.put("total_tokens", usage.getLongValue("total_tokens", input + output));
            canonical.put("usage", canonicalUsage);
        }
        MsCodec.copyUnknown(response, canonical, RP_RESPONSE_FIELDS);
        return canonical;
    }

    /** canonical 响应 → rp 响应。 */
    public static JSONObject fromCanonicalResponse(JSONObject canonical) {
        JSONObject response = new JSONObject();
        response.put("id", canonical.getString("id"));
        response.put("object", "response");
        response.put("model", canonical.getString("model"));
        response.put("status", "completed");
        JSONArray output = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("id", "msg_" + canonical.getString("id"));
        message.put("type", "message");
        message.put("role", "assistant");
        message.put("status", "completed");
        JSONArray content = new JSONArray();
        JSONObject text = new JSONObject();
        text.put("type", "output_text");
        text.put("text", completionText(canonical));
        text.put("annotations", new JSONArray());
        content.add(text);
        message.put("content", content);
        output.add(message);
        response.put("output", output);
        JSONObject usage = canonical.getJSONObject("usage");
        if (usage != null) {
            JSONObject rpUsage = new JSONObject();
            rpUsage.put("input_tokens", usage.getLongValue("prompt_tokens"));
            rpUsage.put("output_tokens", usage.getLongValue("completion_tokens"));
            rpUsage.put("total_tokens", usage.getLongValue("total_tokens"));
            response.put("usage", rpUsage);
        }
        MsCodec.copyUnknown(canonical, response, CC_RESPONSE_FIELDS);
        return response;
    }

    private static String outputText(JSONArray output) {
        if (output == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.size(); i++) {
            JSONObject item = output.getJSONObject(i);
            JSONArray content = item == null ? null : item.getJSONArray("content");
            if (content == null) {
                continue;
            }
            text.append(MsCodec.text(content));
        }
        return text.toString();
    }

    private static String completionText(JSONObject canonical) {
        JSONArray choices = canonical.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message == null ? "" : MsCodec.text(message.get("content"));
    }
}
