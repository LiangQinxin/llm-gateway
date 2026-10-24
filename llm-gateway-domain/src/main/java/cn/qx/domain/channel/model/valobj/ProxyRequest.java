package cn.qx.domain.channel.model.valobj;

import cn.qx.domain.security.model.valobj.AuditResult;
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

    /** 请求入口时间（ISO-8601 带偏移，耗时统计用） */
    private String startedAt;

    /** 链路追踪 ID（日志与安全发现共用） */
    private String traceId;

    /** 脱敏后的落库请求体 */
    private String auditBody;

    /** 请求侧安全扫描结果 */
    private AuditResult auditResult;

    /** 调用方密钥 ID */
    private String apiKeyId;

    /** 调用方密钥名称快照 */
    private String apiKeyName;

    public ProxyRequest(String model, JSONObject body, boolean stream) {
        this(model, body, stream, DEFAULT_INBOUND_PATH, ProtocolFamily.CC, null,
                null, null, null, null, null, null);
    }

}
