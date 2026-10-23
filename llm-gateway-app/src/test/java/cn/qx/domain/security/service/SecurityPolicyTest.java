package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 安全策略矩阵纯函数测试。
 */
public class SecurityPolicyTest {

    @Test
    public void audit_and_warn_modes_follow_matrix() {
        assertEquals(FindingAction.ALLOW,
                SecurityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.AUDIT, false));
        assertEquals(FindingAction.ALLOW,
                SecurityPolicy.actionFor(SecuritySeverity.LOW, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                SecurityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                SecurityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.WARN, false));
    }

    @Test
    public void redact_mode_degrades_non_redactable_findings() {
        assertEquals(FindingAction.REDACT,
                SecurityPolicy.actionFor(SecuritySeverity.HIGH, true, SecurityMode.REDACT, false));
        assertEquals(FindingAction.WARN,
                SecurityPolicy.actionFor(SecuritySeverity.HIGH, false, SecurityMode.REDACT, false));
        assertEquals(FindingAction.ALLOW,
                SecurityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.REDACT, false));
    }

    @Test
    public void block_mode_and_block_critical_follow_matrix() {
        assertEquals(FindingAction.BLOCK,
                SecurityPolicy.actionFor(SecuritySeverity.HIGH, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.ALLOW,
                SecurityPolicy.actionFor(SecuritySeverity.MEDIUM, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.BLOCK,
                SecurityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.AUDIT, true));
    }

    @Test
    public void global_action_uses_combined_risk_level() {
        assertEquals(SecurityAction.BLOCK,
                SecurityPolicy.globalAction(RiskLevel.HIGH, false, SecurityMode.BLOCK, false));
        assertEquals(SecurityAction.SANITIZE,
                SecurityPolicy.globalAction(RiskLevel.HIGH, true, SecurityMode.REDACT, false));
        assertEquals(SecurityAction.ALLOW,
                SecurityPolicy.globalAction(RiskLevel.HIGH, false, SecurityMode.REDACT, false));
        assertEquals(SecurityAction.BLOCK,
                SecurityPolicy.globalAction(RiskLevel.CRITICAL, true, SecurityMode.AUDIT, true));
    }
}
