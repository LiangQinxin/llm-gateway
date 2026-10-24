package cn.qx.domain.security.model.valobj;

/**
 * 发现级动作四值；落库前归一为 {@link SecurityAction} 三值。
 */
public enum FindingAction {
    /** 放行并记录。 */
    ALLOW("allow"),
    /** 记录警告，仍放行。 */
    WARN("warn"),
    /** 脱敏打码后放行。 */
    REDACT("redact"),
    /** 阻断请求。 */
    BLOCK("block");

    /** 动作落库/日志码（小写字符串）。 */
    private final String code;

    FindingAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
