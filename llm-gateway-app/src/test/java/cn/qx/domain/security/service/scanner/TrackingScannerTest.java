package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.SecurityTestSupport;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.RiskLevel;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.ISecurityScanner;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TrackingScanner 端口测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class TrackingScannerTest {

    /** 被测的安全扫描引擎：仅开启追踪类别，经其断言 TrackingScanner 的分级与不参与组合加分。 */
    @Resource
    private ISecurityScanner securityScanner;

    /**
     * 追踪域名与指纹代码分别 Low / Medium，且不参与外联组合加分。
     */
    @Test
    public void tracking_and_fingerprinting_are_classified_without_combo_bonus() {
        String body = """
                https://google-analytics.com/collect
                canvas.toDataURL() navigator.userAgent navigator.geolocation
                """;
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.trackingOnly(SecurityMode.WARN)));
        log.info("追踪扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(rules.contains("tracking_google_analytics"));
        assertTrue(rules.contains("fingerprint_canvas"));
        assertTrue(rules.contains("fingerprint_useragent"));
        assertTrue(rules.contains("tracking_geolocation"));
        assertTrue(result.getFindings().stream()
                .allMatch(f -> f.getCategories().equals(java.util.List.of(SecurityCategory.TRACKING))));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getSeverity() == SecuritySeverity.LOW));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getSeverity() == SecuritySeverity.MEDIUM));
        assertEquals("多个追踪发现只取最高基础分", RiskLevel.MEDIUM, result.getLevel());
        assertEquals("追踪类别不参与组合加分", 35, result.getScore());
    }
}
