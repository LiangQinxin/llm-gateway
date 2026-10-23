package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import org.springframework.stereotype.Component;

/**
 * 安全策略矩阵与动作归一。
 */
@Component
public class SecurityPolicy {

    public FindingAction actionFor(SecuritySeverity severity,
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

    public SecurityAction globalAction(RiskLevel level,
                                       boolean anyRedact,
                                       SecurityMode mode,
                                       boolean blockCritical) {
        SecurityMode effectiveMode = mode == null ? SecurityMode.WARN : mode;
        boolean blockByMode = effectiveMode == SecurityMode.BLOCK
                && (level == RiskLevel.HIGH || level == RiskLevel.CRITICAL);
        boolean blockByCritical = blockCritical && level == RiskLevel.CRITICAL;
        if (blockByMode || blockByCritical) {
            return SecurityAction.BLOCK;
        }
        if (effectiveMode == SecurityMode.REDACT && anyRedact) {
            return SecurityAction.SANITIZE;
        }
        return SecurityAction.ALLOW;
    }
}
