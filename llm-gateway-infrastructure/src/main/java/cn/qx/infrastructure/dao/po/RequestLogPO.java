package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * request_logs 表持久化对象（PO），字段与库表列一一对应。日志只增不改。
 * <p>列名 → 属性名：id、seq、api_key_id→apiKeyId、api_key_name→apiKeyName、
 * channel_id→channelId、channel_name→channelName、model、upstream_model→upstreamModel、
 * mode、protocol_type→protocolType、stream、is_retry→isRetry、status_code→statusCode、
 * prompt_tokens→promptTokens、completion_tokens→completionTokens、total_tokens→totalTokens、
 * duration_ms→durationMs、risk_level→riskLevel、risk_score→riskScore、risk_summary→riskSummary、
 * security_action→securityAction、sanitized、blocked_reason→blockedReason、client_ip→clientIp、
 * error_message→errorMessage、request_body→requestBody、response_choices→responseChoices、
 * trace_id→traceId、created_at→createdAt。
 * JSON 列（request_body / response_choices）保持 String，按需在领域层解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogPO {

    /** id 列：日志 ID（UUID） */
    private String id;
    /** seq 列：自增序列号（数据库自增，insert 后回填） */
    private Long seq;
    /** api_key_id 列：API 密钥 ID（UUID） */
    private String apiKeyId;
    /** api_key_name 列：API 密钥名称（冗余快照） */
    private String apiKeyName;
    /** channel_id 列：命中渠道 ID（UUID） */
    private String channelId;
    /** channel_name 列：命中渠道名称（冗余快照） */
    private String channelName;
    /** model 列：请求模型名 */
    private String model;
    /** upstream_model 列：上游实际模型名（模型映射后） */
    private String upstreamModel;
    /** mode 列：调用模式：chat / completion / embedding */
    private String mode;
    /** protocol_type 列：协议类型：http / sse / websocket */
    private String protocolType;
    /** stream 列：是否流式：1=是，0=否 */
    private Integer stream;
    /** is_retry 列：是否重试请求：1=是，0=否 */
    private Integer isRetry;
    /** status_code 列：上游 HTTP 状态码 */
    private Integer statusCode;
    /** prompt_tokens 列：提示词 token 数 */
    private Long promptTokens;
    /** completion_tokens 列：完成（输出）token 数 */
    private Long completionTokens;
    /** total_tokens 列：总 token 数 */
    private Long totalTokens;
    /** duration_ms 列：请求耗时（毫秒） */
    private Long durationMs;
    /** risk_level 列：风险等级：Clean / Low / Medium / High / Critical（缺省 Clean） */
    private String riskLevel;
    /** risk_score 列：风险评分（缺省 0） */
    private Integer riskScore;
    /** risk_summary 列：风险摘要 */
    private String riskSummary;
    /** security_action 列：安全动作：Allow / Sanitize / Block（缺省 Allow） */
    private String securityAction;
    /** sanitized 列：是否已脱敏：1=是，0=否 */
    private Integer sanitized;
    /** blocked_reason 列：阻断原因（未阻断为空） */
    private String blockedReason;
    /** client_ip 列：调用方客户端 IP */
    private String clientIp;
    /** error_message 列：错误信息（成功为空） */
    private String errorMessage;
    /** request_body 列：请求体 JSON 字符串 */
    private String requestBody;
    /** response_choices 列：响应 choices JSON 字符串 */
    private String responseChoices;
    /** trace_id 列：链路追踪 ID（UUID） */
    private String traceId;
    /** created_at 列：创建时间（ISO-8601 带偏移） */
    private String createdAt;
}
