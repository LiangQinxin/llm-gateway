package cn.qx.domain.security.model.valobj;

/**
 * 风险等级六档；对齐安全审计引擎：Clean 无风险 / Info 提示 / Low 低危 /
 * Medium 中危 / High 高危 / Critical 严重。等级由评分反推，只升不降。
 */
public enum RiskLevel {
    CLEAN,
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * 分数区间定级：≤0 Clean；(0,15) Info；[15,35) Low；[35,65) Medium；
     * [65,90) High；≥90 Critical。基础分取最高 severity，组合加分只会
     * 抬升分数，天然满足"升级不降级"。
     */
    public static RiskLevel fromScore(int score) {
        if (score <= 0) {
            return CLEAN;
        }
        if (score < 15) {
            return INFO;
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
