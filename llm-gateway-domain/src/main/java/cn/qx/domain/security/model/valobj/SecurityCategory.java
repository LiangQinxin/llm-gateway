package cn.qx.domain.security.model.valobj;

/**
 * 风险类别，用于组合加分与归因。追踪类别不参与组合加分。
 */
public enum SecurityCategory {
    CREDENTIAL("credential"),
    SENSITIVE_FILE("sensitive_file"),
    STEGANOGRAPHY("steganography"),
    COMMAND("command"),
    EXFIL("exfil"),
    TRACKING("tracking");

    private final String code;

    SecurityCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
