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

    private ScanPhase phase;
    private List<ScanFinding> findings;
    private int score;
    private RiskLevel level;
    private SecurityAction finalAction;
    private boolean blocked;

    /** 阻断原因；BLOCK 时写入 summary。 */
    private String blockedReason;

    /** 是否发生脱敏（REDACT/SANITIZE 生效）。 */
    private boolean sanitized;

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
