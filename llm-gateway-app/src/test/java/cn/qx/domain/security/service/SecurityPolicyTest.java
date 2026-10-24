package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

/**
 * 安全策略矩阵测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityPolicyTest {

    @Resource
    private SecurityPolicy securityPolicy;

    @Test
    public void complete_mode_and_severity_matrix() {
        for (SecuritySeverity severity : SecuritySeverity.values()) {
            assertEquals(FindingAction.ALLOW,
                    securityPolicy.actionFor(severity, true, SecurityMode.AUDIT, false));
        }
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.LOW, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.HIGH, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.REDACT, false));
        assertEquals(FindingAction.REDACT,
                securityPolicy.actionFor(SecuritySeverity.HIGH, true, SecurityMode.REDACT, false));
        assertEquals(FindingAction.REDACT,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.REDACT, false));
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.BLOCK,
                securityPolicy.actionFor(SecuritySeverity.HIGH, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.BLOCK,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.BLOCK,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.AUDIT, true));
        log.info("完整策略矩阵已覆盖: modes={}, severities={}",
                SecurityMode.values().length, SecuritySeverity.values().length);
    }

    @Test
    public void off_and_confirm_modes_follow_engine_reference() {
        for (SecuritySeverity severity : SecuritySeverity.values()) {
            assertEquals("OFF 档只审计放行", FindingAction.ALLOW,
                    securityPolicy.actionFor(severity, true, SecurityMode.OFF, false));
            assertEquals("CONFIRM 档预留放行", FindingAction.ALLOW,
                    securityPolicy.actionFor(severity, true, SecurityMode.CONFIRM, false));
        }
    }

    @Test
    public void audit_and_warn_modes_follow_matrix() {
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.AUDIT, false));
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.LOW, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.WARN, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.WARN, false));
    }

    @Test
    public void redact_mode_degrades_non_redactable_findings() {
        assertEquals(FindingAction.REDACT,
                securityPolicy.actionFor(SecuritySeverity.HIGH, true, SecurityMode.REDACT, false));
        assertEquals(FindingAction.WARN,
                securityPolicy.actionFor(SecuritySeverity.HIGH, false, SecurityMode.REDACT, false));
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, true, SecurityMode.REDACT, false));
    }

    @Test
    public void block_mode_and_block_critical_follow_matrix() {
        assertEquals(FindingAction.BLOCK,
                securityPolicy.actionFor(SecuritySeverity.HIGH, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.ALLOW,
                securityPolicy.actionFor(SecuritySeverity.MEDIUM, false, SecurityMode.BLOCK, false));
        assertEquals(FindingAction.BLOCK,
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.AUDIT, true));
    }

    @Test
    public void global_action_uses_combined_risk_level() {
        assertEquals(SecurityAction.BLOCK,
                securityPolicy.globalAction(RiskLevel.HIGH, false, SecurityMode.BLOCK, false));
        assertEquals(SecurityAction.SANITIZE,
                securityPolicy.globalAction(RiskLevel.HIGH, true, SecurityMode.REDACT, false));
        assertEquals(SecurityAction.ALLOW,
                securityPolicy.globalAction(RiskLevel.HIGH, false, SecurityMode.REDACT, false));
        assertEquals(SecurityAction.BLOCK,
                securityPolicy.globalAction(RiskLevel.CRITICAL, true, SecurityMode.AUDIT, true));
        log.info("策略矩阵: {}", JSON.toJSONString(new Object[]{
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.WARN, false),
                securityPolicy.globalAction(RiskLevel.HIGH, true, SecurityMode.REDACT, false)
        }));
    }
}
