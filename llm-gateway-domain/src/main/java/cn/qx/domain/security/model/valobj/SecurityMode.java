package cn.qx.domain.security.model.valobj;

import java.util.Locale;

/**
 * 全局安全策略档位；对齐安全审计引擎六档：
 * OFF/AUDIT 只审计放行，WARN 中危以上警告，REDACT 高危以上脱敏，
 * CONFIRM 预留待确认，BLOCK 高危以上阻断。blockCritical 兜底见 {@link SecurityPolicy}。
 */
public enum SecurityMode {
    OFF,
    AUDIT,
    WARN,
    REDACT,
    CONFIRM,
    BLOCK;

    public static SecurityMode parse(String value) {
        if (value == null || value.isBlank()) {
            return WARN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WARN;
        }
    }
}
