package cn.qx.domain.channel.model.valobj;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理请求——统一的上游请求抽象。
 * <p>body 用 JSONObject 透传：OpenAI 协议字段多（tools / response_format / logprobs 等），保持最大兼容性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyRequest {

    /** 下游请求的模型名（映射前原始值） */
    private String model;

    /** 完整请求体（OpenAI 格式 JSON） */
    private JSONObject body;

    /** 是否流式转发 */
    private boolean stream;
}
