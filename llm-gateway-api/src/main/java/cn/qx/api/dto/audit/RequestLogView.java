package cn.qx.api.dto.audit;

import cn.qx.domain.audit.model.entity.RequestLog;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求日志管理面输出对象。
 * <p>与前端约定：stream / isRetry / sanitized 由整数（1/0）转换为布尔展示字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogView {

    /** 日志 ID（UUID） */
    private String id;
    /** API 密钥 ID */
    private String apiKeyId;
    /** API 密钥名称 */
    private String apiKeyName;
    /** 命中渠道 ID */
    private String channelId;
    /** 命中渠道名称 */
    private String channelName;
    /** 请求模型名 */
    private String model;
    /** 上游实际模型名 */
    private String upstreamModel;
    /** 调用模式：chat / completion / embedding */
    private String mode;
    /** 是否流式（固定输出名 isStream，与前端契约一致） */
    @JsonProperty("isStream")
    private boolean isStream;
    /** 是否重试请求（固定输出名 isRetry） */
    @JsonProperty("isRetry")
    private boolean isRetry;
    /** 上游 HTTP 状态码 */
    private Integer statusCode;
    /** 提示词 token 数 */
    private Long promptTokens;
    /** 完成 token 数 */
    private Long completionTokens;
    /** 总 token 数 */
    private Long totalTokens;
    /** 请求耗时（毫秒） */
    private Long durationMs;
    /** 风险等级 */
    private String riskLevel;
    /** 风险评分 */
    private Integer riskScore;
    /** 风险摘要 */
    private String riskSummary;
    /** 安全动作 */
    private String securityAction;
    /** 是否已脱敏 */
    private boolean sanitized;
    /** 阻断原因 */
    private String blockedReason;
    /** 客户端 IP */
    private String clientIp;
    /** 错误信息 */
    private String errorMessage;
    /** 请求体 JSON 字符串 */
    private String requestBody;
    /** 响应 choices JSON 字符串 */
    private String responseChoices;
    /** 链路追踪 ID */
    private String traceId;
    /** 创建时间 */
    private String createdAt;

    /** 领域实体 → 管理面输出对象。 */
    public static RequestLogView from(RequestLog log) {
        if (log == null) {
            return null;
        }
        return RequestLogView.builder()
                .id(log.getId())
                .apiKeyId(log.getApiKeyId())
                .apiKeyName(log.getApiKeyName())
                .channelId(log.getChannelId())
                .channelName(log.getChannelName())
                .model(log.getModel())
                .upstreamModel(log.getUpstreamModel())
                .mode(log.getMode())
                .isStream(log.getStream() != null && log.getStream() == 1)
                .isRetry(log.getIsRetry() != null && log.getIsRetry() == 1)
                .statusCode(log.getStatusCode())
                .promptTokens(log.getPromptTokens())
                .completionTokens(log.getCompletionTokens())
                .totalTokens(log.getTotalTokens())
                .durationMs(log.getDurationMs())
                .riskLevel(log.getRiskLevel())
                .riskScore(log.getRiskScore())
                .riskSummary(log.getRiskSummary())
                .securityAction(log.getSecurityAction())
                .sanitized(log.getSanitized() != null && log.getSanitized() == 1)
                .blockedReason(log.getBlockedReason())
                .clientIp(log.getClientIp())
                .errorMessage(log.getErrorMessage())
                .requestBody(log.getRequestBody())
                .responseChoices(log.getResponseChoices())
                .traceId(log.getTraceId())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
