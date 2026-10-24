package cn.qx.api.dto.security;

import cn.qx.domain.security.model.valobj.AuditResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求侧安全决策结果：转发体、脱敏落库体、审计结论与阻断标记。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditOutcome {

    /** 放行/脱敏后的上游转发请求体 */
    private String forwardBody;
    /** 敏感信息已移除的落库请求体 */
    private String auditBody;
    /** 安全扫描结论 */
    private AuditResult audit;
    /** 是否应阻断 */
    private boolean blocked;
}
