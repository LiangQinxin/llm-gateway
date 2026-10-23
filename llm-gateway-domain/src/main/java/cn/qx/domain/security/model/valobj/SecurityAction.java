package cn.qx.domain.security.model.valobj;

/**
 * 落库安全动作三值。
 */
public enum SecurityAction {
    ALLOW("Allow"),
    SANITIZE("Sanitize"),
    BLOCK("Block");

    private final String code;

    SecurityAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
