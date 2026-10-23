package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 三段评分：最高基础分 → 类别组合加分 → 分数区间定级。
 */
@Component
public class SecurityRiskScorer {

    public RiskAssessment score(List<ScanFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new RiskAssessment(0, RiskLevel.CLEAN);
        }
        int base = findings.stream()
                .map(ScanFinding::getSeverity)
                .filter(severity -> severity != null)
                .mapToInt(SecuritySeverity::score)
                .max()
                .orElse(0);
        Set<SecurityCategory> categories = EnumSet.noneOf(SecurityCategory.class);
        for (ScanFinding finding : findings) {
            if (finding.getCategories() != null) {
                categories.addAll(finding.getCategories());
            }
        }
        int combination = 0;
        if (categories.contains(SecurityCategory.CREDENTIAL)
                && categories.contains(SecurityCategory.EXFIL)) {
            combination += 25;
        }
        if (categories.contains(SecurityCategory.SENSITIVE_FILE)
                && categories.contains(SecurityCategory.EXFIL)) {
            combination += 25;
        }
        if (categories.contains(SecurityCategory.STEGANOGRAPHY)
                && categories.contains(SecurityCategory.EXFIL)) {
            combination += 15;
        }
        if (categories.contains(SecurityCategory.COMMAND)
                && categories.contains(SecurityCategory.SENSITIVE_FILE)) {
            combination += 20;
        }
        int score = base + combination;
        return new RiskAssessment(score, RiskLevel.fromScore(score));
    }

    public record RiskAssessment(int score, RiskLevel level) {
    }
}
