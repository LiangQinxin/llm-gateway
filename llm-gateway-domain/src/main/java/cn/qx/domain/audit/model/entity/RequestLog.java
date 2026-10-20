package cn.qx.domain.audit.model.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求审计日志，对应 request_logs 表。日志只增不改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLog {

    /** 日志 ID（UUID） */
    private String id;

    /** 自增序列号（数据库自增） */
    private Long seq;

    /** API 密钥 ID（UUID） */
    private String apiKeyId;

    /** API 密钥名称（冗余快照） */
    private String apiKeyName;

    /** 命中渠道 ID（UUID） */
    private String channelId;

    /** 命中渠道名称（冗余快照） */
    private String channelName;

    /** 请求模型名 */
    private String model;

    /** 上游实际模型名（模型映射后） */
    private String upstreamModel;

    /** 调用模式：chat / completion / embedding */
    private String mode;

    /** 协议类型：http / sse / websocket */
    private String protocolType;

    /** 是否流式：1=是，0=否 */
    private Integer stream;

    /** 是否重试请求：1=是，0=否 */
    private Integer isRetry;

    /** 上游 HTTP 状态码 */
    private Integer statusCode;

    /** 提示词 token 数 */
    private Long promptTokens;

    /** 完成（输出）token 数 */
    private Long completionTokens;

    /** 总 token 数 */
    private Long totalTokens;

    /** 请求耗时（毫秒） */
    private Long durationMs;

    /** 风险等级：Clean / Low / Medium / High / Critical（缺省 Clean） */
    private String riskLevel;

    /** 风险评分（缺省 0） */
    private Integer riskScore;

    /** 风险摘要 */
    private String riskSummary;

    /** 安全动作：Allow / Sanitize / Block（缺省 Allow） */
    private String securityAction;

    /** 是否已脱敏：1=是，0=否 */
    private Integer sanitized;

    /** 阻断原因（未阻断为空） */
    private String blockedReason;

    /** 调用方客户端 IP */
    private String clientIp;

    /** 错误信息（成功为空） */
    private String errorMessage;

    /** 请求体 JSON 字符串 */
    private String requestBody;

    /** 响应 choices JSON 字符串 */
    private String responseChoices;

    /** 链路追踪 ID（UUID） */
    private String traceId;

    /** 创建时间（ISO-8601 带偏移） */
    private String createdAt;

    /** 按需解析请求体，不在 MyBatis 中做 JSON 自动转换。 */
    public JSONObject requestBodyJson() {
        return isBlank(requestBody) ? null : JSON.parseObject(requestBody);
    }

    /** 按需解析响应 choices。 */
    public JSONArray responseChoicesJson() {
        return isBlank(responseChoices) ? null : JSON.parseArray(responseChoices);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
