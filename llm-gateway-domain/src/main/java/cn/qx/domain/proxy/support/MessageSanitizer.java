package cn.qx.domain.proxy.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 与渠道无关的 OpenAI 兼容消息清洗规则。
 */
public final class MessageSanitizer {

    private MessageSanitizer() {
    }

    /**
     * 清理空 assistant content：无 tool_calls 删除整条消息；有 tool_calls 时 content 置 null。
     */
    public static JSONObject sanitizeMessages(JSONObject body) {
        if (body == null) {
            return null;
        }
        JSONArray messages = body.getJSONArray("messages");
        if (messages == null) {
            return body;
        }
        for (int i = 0; i < messages.size(); i++) {
            JSONObject message = messages.getJSONObject(i);
            if (message == null || !"assistant".equals(message.getString("role"))
                    || !isEmptyContent(message.get("content"))) {
                continue;
            }
            JSONArray toolCalls = message.getJSONArray("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                message.put("content", null);
            } else {
                messages.remove(i);
                i--;
            }
        }
        return body;
    }

    private static boolean isEmptyContent(Object content) {
        if (content == null) {
            return true;
        }
        if (content instanceof String value) {
            return value.isEmpty();
        }
        if (content instanceof JSONArray value) {
            return value.isEmpty();
        }
        return false;
    }
}
