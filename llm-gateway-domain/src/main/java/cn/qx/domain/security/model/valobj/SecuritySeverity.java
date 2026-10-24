package cn.qx.domain.security.model.valobj;

/**
 * 单条发现的严重度；基础分取本次扫描的最高 severity，不做数量累加。
 */
public enum SecuritySeverity {
    INFO(5),
    LOW(15),
    MEDIUM(35),
    HIGH(65),
    CRITICAL(90);

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
