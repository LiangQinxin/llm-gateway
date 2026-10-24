package cn.qx.api.dto.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据面一次请求的审计日志快照。
 * <p>由 proxy 用例组装，交给 audit 域单写者落库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPlaneLogData {

    /** API 密钥 ID */
    private String apiKeyId;
    /** API 密钥名称快照 */
    private String apiKeyName;
    /** 命中或最后尝试的渠道 ID */
    private String channelId;
    /** 渠道名称快照 */
    private String channelName;
    /** 请求模型 */
    private String model;
    /** 上游实际模型 */
    private String upstreamModel;
    /** 调用模式 */
    private String mode;
    /** 协议类型 */
    private String protocolType;
    /** 是否流式 */
    private Integer stream;
    /** 是否经历 failover */
    private Integer isRetry;
    /** HTTP 状态码 */
    private Integer statusCode;
    /** 提示词 token */
    private Long promptTokens;
    /** 完成 token */
    private Long completionTokens;
    /** 总 token */
    private Long totalTokens;
    /** 耗时毫秒 */
    private Long durationMs;
    /** 风险等级 */
    private String riskLevel;
    /** 风险分 */
    private Integer riskScore;
    /** 风险摘要 */
    private String riskSummary;
    /** 安全动作 */
    private String securityAction;
    /** 是否脱敏 */
    private Integer sanitized;
    /** 阻断原因 */
    private String blockedReason;
    /** 调用方 IP */
    private String clientIp;
    /** 错误信息 */
    private String errorMessage;
    /** 脱敏后的请求体 */
    private String requestBody;
    /** 响应 choices JSON */
    private String responseChoices;
    /** 链路追踪 ID */
    private String traceId;
}
