package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 安全策略矩阵测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityPolicyTest {

    /** 被测的安全策略：按严重度与审计模式矩阵判定放行/告警/脱敏/阻断动作。 */
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
    public void global_action_uses_strictest_finding_action() {
        assertEquals(SecurityAction.BLOCK,
                securityPolicy.globalAction(List.of(
                        FindingAction.ALLOW, FindingAction.WARN,
                        FindingAction.REDACT, FindingAction.BLOCK)));
        assertEquals(SecurityAction.SANITIZE,
                securityPolicy.globalAction(List.of(
                        FindingAction.ALLOW, FindingAction.WARN, FindingAction.REDACT)));
        assertEquals(SecurityAction.ALLOW,
                securityPolicy.globalAction(List.of(FindingAction.ALLOW, FindingAction.WARN)));
        assertEquals(SecurityAction.ALLOW, securityPolicy.globalAction(List.of()));
        log.info("策略矩阵: {}", JSON.toJSONString(new Object[]{
                securityPolicy.actionFor(SecuritySeverity.CRITICAL, true, SecurityMode.WARN, false),
                securityPolicy.globalAction(List.of(FindingAction.REDACT))
        }));
    }

    @Test
    public void rule_action_parser_only_accepts_overrides() {
        assertEquals(FindingAction.BLOCK, FindingAction.fromRuleAction("BLOCK"));
        assertEquals(FindingAction.REDACT, FindingAction.fromRuleAction(" redact "));
        assertEquals(FindingAction.WARN, FindingAction.fromRuleAction("warn"));
        assertNull("allow 不是规则级覆盖动作", FindingAction.fromRuleAction("allow"));
        assertNull("未知动作应回落全局策略", FindingAction.fromRuleAction("unknown"));
        assertNull(FindingAction.fromRuleAction(""));
    }
}
