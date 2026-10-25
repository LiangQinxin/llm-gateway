package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 安全策略矩阵与动作归一；对齐安全审计引擎 decide_action：
 * - OFF/AUDIT：全部放行仅记录；CONFIRM 预留，等同放行
 * - WARN：Medium 以上 Warn（仍放行）
 * - REDACT：High 以上 Redact（不可脱敏降级 Warn）
 * - BLOCK：High 以上 Block
 * - blockCritical 兜底：Critical 无视档位强制 Block
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
            case OFF, AUDIT, CONFIRM -> FindingAction.ALLOW;
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

    public SecurityAction globalAction(Collection<FindingAction> findingActions) {
        boolean redact = false;
        if (findingActions != null) {
            for (FindingAction action : findingActions) {
                if (action == FindingAction.BLOCK) {
                    return SecurityAction.BLOCK;
                }
                redact |= action == FindingAction.REDACT;
            }
        }
        return redact ? SecurityAction.SANITIZE : SecurityAction.ALLOW;
    }
}
