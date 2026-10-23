package cn.qx.domain.security.model.valobj;

import java.util.Locale;

/**
 * 全局安全策略档位。
 */
public enum SecurityMode {
    AUDIT,
    WARN,
    REDACT,
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
