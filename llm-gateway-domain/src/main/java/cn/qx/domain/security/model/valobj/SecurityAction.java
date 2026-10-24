package cn.qx.domain.security.model.valobj;

/**
 * 落库安全动作三值。
 */
public enum SecurityAction {
    /** 放行并记录。 */
    ALLOW("Allow"),
    /** 打码脱敏后放行。 */
    SANITIZE("Sanitize"),
    /** 拦截阻断。 */
    BLOCK("Block");

    /** 落库动作码（RequestLog.securityAction 取值）。 */
    private final String code;

    SecurityAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
