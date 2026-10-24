package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 安全扫描输出；对齐安全审计引擎 SecurityScanResult：
 * risk_level / risk_score / action / sanitized / blocked_reason / summary / findings。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    /** 扫描方向（请求体 REQUEST / 响应体 RESPONSE）。 */
    private ScanPhase phase;
    /** 本次扫描命中的安检发现列表。 */
    private List<ScanFinding> findings;
    /** 三段制风险总分（0–100，封顶反推等级）。 */
    private int score;
    /** 由总分反推的风险等级（只升不降）。 */
    private RiskLevel level;
    /** 归一后的落库安全动作（Allow/Sanitize/Block）。 */
    private SecurityAction finalAction;
    /** 是否被阻断（finalAction=Block 时为 true）。 */
    private boolean blocked;

    /** 阻断原因；BLOCK 时写入 summary。 */
    private String blockedReason;

    /** 是否发生脱敏（REDACT/SANITIZE 生效）。 */
    private boolean sanitized;

    /** 人读扫描结论摘要。 */
    private String summary;

    public static AuditResult clean(ScanPhase phase, String summary) {
        return AuditResult.builder()
                .phase(phase)
                .findings(List.of())
                .score(0)
                .level(RiskLevel.CLEAN)
                .finalAction(SecurityAction.ALLOW)
                .blocked(false)
                .sanitized(false)
                .summary(summary)
                .build();
    }
}
