package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;

/**
 * 安全策略矩阵与动作归一。
 */
public final class SecurityPolicy {

    private SecurityPolicy() {
    }

    public static FindingAction actionFor(SecuritySeverity severity,
                                          boolean redactable,
                                          SecurityMode mode,
                                          boolean blockCritical) {
        if (severity == null) {
            return FindingAction.ALLOW;
        }
        if (blockCritical && severity == SecuritySeverity.CRITICAL) {
            return FindingAction.BLOCK;
        }
        SecurityMode effectiveMode = mode == null ? SecurityMode.WARN : mode;
        return switch (effectiveMode) {
            case AUDIT -> FindingAction.ALLOW;
            case WARN -> switch (severity) {
                case INFO, LOW -> FindingAction.ALLOW;
                case MEDIUM, HIGH, CRITICAL -> FindingAction.WARN;
            };
            case REDACT -> switch (severity) {
                case INFO, LOW, MEDIUM -> FindingAction.ALLOW;
                case HIGH, CRITICAL -> redactable ? FindingAction.REDACT : FindingAction.WARN;
            };
            case BLOCK -> switch (severity) {
                case INFO, LOW, MEDIUM -> FindingAction.ALLOW;
                case HIGH, CRITICAL -> FindingAction.BLOCK;
            };
        };
    }

    public static SecurityAction globalAction(RiskLevel level,
                                              boolean anyRedactable,
                                              SecurityMode mode,
                                              boolean blockCritical) {
        FindingAction action = actionFor(severityFor(level), anyRedactable, mode, blockCritical);
        return switch (action) {
            case BLOCK -> SecurityAction.BLOCK;
            case REDACT -> SecurityAction.SANITIZE;
            case ALLOW, WARN -> SecurityAction.ALLOW;
        };
    }

    private static SecuritySeverity severityFor(RiskLevel level) {
        return switch (level) {
            case CLEAN, LOW -> SecuritySeverity.LOW;
            case MEDIUM -> SecuritySeverity.MEDIUM;
            case HIGH -> SecuritySeverity.HIGH;
            case CRITICAL -> SecuritySeverity.CRITICAL;
        };
    }
}
