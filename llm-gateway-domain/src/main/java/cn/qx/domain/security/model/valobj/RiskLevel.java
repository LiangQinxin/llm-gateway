package cn.qx.domain.security.model.valobj;

/**
 * 三段评分后的风险等级。
 */
public enum RiskLevel {
    CLEAN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(int score) {
        if (score <= 0) {
            return CLEAN;
        }
        if (score < 35) {
            return LOW;
        }
        if (score < 65) {
            return MEDIUM;
        }
        if (score < 90) {
            return HIGH;
        }
        return CRITICAL;
    }

    public String code() {
        return name().toLowerCase();
    }
}
