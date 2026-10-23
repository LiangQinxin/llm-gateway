package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 安全扫描输出。
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
    private String summary;

    public static AuditResult clean(ScanPhase phase, String summary) {
        return AuditResult.builder()
                .phase(phase)
                .findings(List.of())
                .score(0)
                .level(RiskLevel.CLEAN)
                .finalAction(SecurityAction.ALLOW)
                .blocked(false)
                .summary(summary)
                .build();
    }
}
