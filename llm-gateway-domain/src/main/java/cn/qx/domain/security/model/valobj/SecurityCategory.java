package cn.qx.domain.security.model.valobj;

/**
 * 风险类别，用于组合加分与归因。追踪类别不参与组合加分。
 */
public enum SecurityCategory {
    /** 凭证（云厂商密钥、私钥块、账号密码泄露）。 */
    CREDENTIAL("credential"),
    /** 敏感文件（~/.ssh、/etc/passwd 等路径引用）。 */
    SENSITIVE_FILE("sensitive_file"),
    /** 隐写（Unicode 伪装字符、零宽/方向控制符）。 */
    STEGANOGRAPHY("steganography"),
    /** 命令（危险命令、反弹 shell、外发工具调用）。 */
    COMMAND("command"),
    /** 外联/外泄（外发 URL、内网探测、数据外带目标）。 */
    EXFIL("exfil"),
    /** 追踪（跟踪像素、指纹采集；不参与组合加分）。 */
    TRACKING("tracking");

    /** 类别落库码（小写字符串）。 */
    private final String code;

    SecurityCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
