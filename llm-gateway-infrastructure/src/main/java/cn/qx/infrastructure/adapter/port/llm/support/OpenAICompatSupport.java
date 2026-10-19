package cn.qx.infrastructure.adapter.port.llm.support;

import com.alibaba.fastjson2.JSONObject;

/**
 * OpenAI 兼容渠道的共享请求处理。
 * <p>字符串映射在此应用；数组映射不在适配器采样——
 * 采样（等权随机 / 权重）在路由阶段执行（ModelMapping.resolve），一次采样预烘焙进 body 后，
 * 适配器这里看到数组值也原样保留（不重采样，保证日志记录的模型与实际请求模型同源）。
 */
public final class OpenAICompatSupport {

    private OpenAICompatSupport() {
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
     *
     * @param body    请求体（OpenAI 格式 JSON）
     * @param mapping 模型映射表
     * @return 改写后的请求体
     */
    public static JSONObject applyModelMapping(JSONObject body, JSONObject mapping) {
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
}
