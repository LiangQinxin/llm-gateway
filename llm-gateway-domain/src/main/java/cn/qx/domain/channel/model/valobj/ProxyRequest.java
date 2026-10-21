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

    /** 默认入站路径 */
    public static final String DEFAULT_INBOUND_PATH = "/v1/chat/completions";

    /** 下游请求的模型名（映射前原始值） */
    private String model;

    /** 完整请求体（OpenAI 格式 JSON） */
    private JSONObject body;

    /** 是否流式转发 */
    private boolean stream;

    /** 入站数据面路径（ADR-0011 扩展：供适配器选择原生端点，channel 域仍不解析 body） */
    private String inboundPath;

    /** 入站协议族（ADR-0011 P_in） */
    private ProtocolFamily protocol;

    /** 入站原始字节（用于原生端点零转换透传） */
    private String rawBody;

    public ProxyRequest(String model, JSONObject body, boolean stream) {
        this(model, body, stream, DEFAULT_INBOUND_PATH, ProtocolFamily.CC, null);
    }

}
