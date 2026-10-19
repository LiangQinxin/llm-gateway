package cn.qx.domain.channel.model.valobj;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Random;

/**
 * 模型映射值对象——下游请求模型名 → 上游真实模型名。
 * <p>字符串值一对一确定；数组值按权重采样（本期等权随机）。
 * <p>采样在路由阶段执行一次并预烘焙进请求体，保证日志记录的模型与实际请求模型同源；
 * 适配器只应用映射结果，不自行采样。
 */
public class ModelMapping {

    /** 映射表：key=下游模型名，value=上游模型名（字符串或数组） */
    private final JSONObject mapping;

    /**
     * 构造模型映射。
     *
     * @param mapping 映射表，null 视为空映射
     */
    public ModelMapping(JSONObject mapping) {
        this.mapping = mapping != null ? mapping : new JSONObject();
    }

    /**
     * 一次采样：解析下游请求模型 → 上游模型。
     * <p>规则：
     * <ul>
     *   <li>字符串直接映射；</li>
     *   <li>数组等权随机采样；</li>
     *   <li>未命中 / 空值原样返回。</li>
     * </ul>
     * 调用方（路由计划）将结果预烘焙进 body.model，再交给适配器透传。
     *
     * @param requestedModel 下游请求的模型名
     * @param random         随机源（数组采样用）
     * @return 上游真实模型名
     */
    public String resolve(String requestedModel, Random random) {
        Object mapped = mapping.get(requestedModel);
        if (mapped == null) {
            return requestedModel;
        }
        if (mapped instanceof String s) {
            return s.isEmpty() ? requestedModel : s;
        }
        if (mapped instanceof JSONArray arr && !arr.isEmpty()) {
            return arr.getString(random.nextInt(arr.size()));
        }
        return requestedModel;
    }

    /** 映射表是否为空 */
    public boolean isEmpty() {
        return mapping.isEmpty();
    }
}
