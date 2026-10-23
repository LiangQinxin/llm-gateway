package cn.qx.domain.security.model.valobj;

/**
 * 发现级动作四值；落库前归一为 {@link SecurityAction} 三值。
 */
public enum FindingAction {
    ALLOW("allow"),
    WARN("warn"),
    REDACT("redact"),
    BLOCK("block");

    private final String code;

    FindingAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
