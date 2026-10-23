package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.SecurityTestSupport;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecurityMode;
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
 * UnicodeScanner 端口测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class UnicodeScannerTest {

    @Resource
    private ISecurityScanner securityScanner;

    /**
     * 零宽、Bidi、私用区与同形字均命中隐写类别。
     */
    @Test
    public void invisible_and_mixed_script_characters_are_detected() {
        String body = "a\u200Bb \u202Eevil\u202C \uE000 token p\u0430ypal";
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.unicodeOnly(SecurityMode.WARN)));
        log.info("Unicode 扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(rules.contains("unicode_zero_width"));
        assertTrue(rules.contains("unicode_bidi"));
        assertTrue(rules.contains("unicode_pua"));
        assertTrue(rules.contains("unicode_homoglyph"));
        assertTrue(result.getFindings().stream()
                .allMatch(f -> f.getCategories().contains(SecurityCategory.STEGANOGRAPHY)));
    }

    /**
     * redact 档下不可见字符可 Sanitize，同形字不可打码降级 Warn。
     */
    @Test
    public void redact_mode_only_sanitizes_redactable_findings() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "a\u202Eevil\u202C https://webhook.site/x p\u0430ypal",
                SecurityTestSupport.unicodeOnly(SecurityMode.REDACT).toBuilder()
                        .network(true).build()));
        log.info("Unicode redact 结果: {}", JSON.toJSONString(result));

        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "unicode_bidi".equals(f.getRuleId())
                        && f.getAction() == FindingAction.REDACT));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "unicode_homoglyph".equals(f.getRuleId())
                        && f.getAction() == FindingAction.ALLOW));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "network_exfil_target".equals(f.getRuleId())
                        && f.getAction() == FindingAction.WARN));
        assertEquals(SecurityAction.SANITIZE, result.getFinalAction());
    }
}
