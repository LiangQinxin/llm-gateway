package cn.qx.domain.security.model.valobj;

/**
 * 单条发现的严重度；基础分取本次扫描的最高 severity，不做数量累加。
 */
public enum SecuritySeverity {
    /** 信息级（基础分 5）。 */
    INFO(5),
    /** 低危（基础分 15）。 */
    LOW(15),
    /** 中危（基础分 35）。 */
    MEDIUM(35),
    /** 高危（基础分 65）。 */
    HIGH(65),
    /** 严重（基础分 90）。 */
    CRITICAL(90);

    /** 该严重度对应的三段制基础分。 */
    private final int score;

    SecuritySeverity(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }

    public String code() {
        return name().toLowerCase();
    }

    public static SecuritySeverity parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
