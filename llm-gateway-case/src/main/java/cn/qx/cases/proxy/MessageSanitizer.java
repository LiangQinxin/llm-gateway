package cn.qx.cases.proxy;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * OpenAI 兼容上游共享的消息清洗规则。
 */
public final class MessageSanitizer {

    private MessageSanitizer() {
    }

    /**
     * 清理空 content 的 assistant 消息：
     * 无 tool_calls 整条删除；有 tool_calls 时 content 置 null。
     */
    public static void sanitizeMessages(JSONObject body) {
        if (body == null) {
            return;
        }
        JSONArray messages = body.getJSONArray("messages");
        if (messages == null) {
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            JSONObject message = messages.getJSONObject(i);
            if (message == null || !"assistant".equals(message.getString("role"))
                    || !isEmptyContent(message.get("content"))) {
                continue;
            }
            if (hasToolCalls(message)) {
                message.put("content", null);
            } else {
                messages.remove(i);
                i--;
            }
        }
    }

    private static boolean hasToolCalls(JSONObject message) {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        return toolCalls != null && !toolCalls.isEmpty();
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
