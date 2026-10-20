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

    private String id;
    private Long seq;
    private String apiKeyId;
    private String apiKeyName;
    private String channelId;
    private String channelName;
    private String model;
    private String upstreamModel;
    private String mode;
    private String protocolType;
    private Integer stream;
    private Integer isRetry;
    private Integer statusCode;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Long durationMs;
    private String riskLevel;
    private Integer riskScore;
    private String riskSummary;
    private String securityAction;
    private Integer sanitized;
    private String blockedReason;
    private String clientIp;
    private String errorMessage;
    private String requestBody;
    private String responseChoices;
    private String traceId;
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
