package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 三段评分纯函数测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityRiskScorerTest {

    /** 被测的风险评分器：对多条扫描发现做基础分与组合加分并按 100 封顶。 */
    @Resource
    private SecurityRiskScorer riskScorer;

    @Test
    public void score_uses_max_base_and_combination_bonuses() {
        List<ScanFinding> findings = List.of(
                finding(SecuritySeverity.CRITICAL, SecurityCategory.CREDENTIAL),
                finding(SecuritySeverity.MEDIUM, SecurityCategory.EXFIL),
                finding(SecuritySeverity.LOW, SecurityCategory.SENSITIVE_FILE));
        SecurityRiskScorer.RiskAssessment assessment = riskScorer.score(findings);
        log.info("评分组合: {}", JSON.toJSONString(assessment));
        assertEquals("90(基础) + 25 + 25 = 140，封顶 100",
                100, assessment.score());
        assertEquals(RiskLevel.CRITICAL, assessment.level());
    }

    @Test
    public void score_caps_at_100_when_combo_overflows() {
        List<ScanFinding> findings = List.of(
                finding(SecuritySeverity.CRITICAL, SecurityCategory.CREDENTIAL),
                finding(SecuritySeverity.CRITICAL, SecurityCategory.EXFIL),
                finding(SecuritySeverity.CRITICAL, SecurityCategory.SENSITIVE_FILE));
        SecurityRiskScorer.RiskAssessment assessment = riskScorer.score(findings);
        log.info("封顶评分: {}", JSON.toJSONString(assessment));
        assertEquals(100, assessment.score());
        assertEquals(RiskLevel.CRITICAL, assessment.level());
    }

    @Test
    public void risk_level_bands_are_exact() {
        assertEquals(RiskLevel.CLEAN, RiskLevel.fromScore(0));
        assertEquals(RiskLevel.INFO, RiskLevel.fromScore(5));
        assertEquals(RiskLevel.INFO, RiskLevel.fromScore(14));
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(15));
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(34));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(35));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(64));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(65));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(89));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(90));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(100));
    }

    private static ScanFinding finding(SecuritySeverity severity, SecurityCategory category) {
        return ScanFinding.builder()
                .severity(severity)
                .categories(List.of(category))
                .build();
    }
}
