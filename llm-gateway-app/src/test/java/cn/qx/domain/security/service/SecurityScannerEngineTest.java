package cn.qx.domain.security.service;

import cn.qx.domain.security.SecurityTestSupport;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SecurityScanner 引擎端口测试：JSON 钻取、原文兜底、边界、评分、打码与策略。
 * 评分与动作语义对齐安全审计引擎：min(100, 最高基础分+组合加分)、
 * evidence_masked 打码、MAX_FINDINGS=80、blocked_reason/sanitized、响应侧开关。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityScannerEngineTest {

    /** 被测的安全扫描引擎：断言 JSON 钻取、评分、打码、截断与策略动作。 */
    @Resource
    private ISecurityScanner securityScanner;

    /**
     * JSON 字符串值按 JSONPath 定位，同时原文整体扫描兜底。
     */
    @Test
    public void scans_json_string_values_and_raw_body() {
        String body = "{\"messages\":[{\"content\":\"sk-proj-abcdefghijklmnopqrstuvwx\"}]}";
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.AUDIT)));
        log.info("JSON 钻取结果: {}", JSON.toJSONString(result));

        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "$.messages[0].content".equals(f.getLocation())));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getLocation().startsWith("@raw[")));
    }

    /**
     * JSON 字段名只在 @raw 兜底扫描中命中赋值类凭证规则。
     */
    @Test
    public void raw_scan_covers_json_field_names() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "{\"password\":\"abc12345\"}", SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("字段名兜底结果: {}", JSON.toJSONString(result));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "credential_password".equals(f.getRuleId())
                        && f.getLocation().startsWith("@raw[")));
    }

    /**
     * Critical 基础分 90 + 凭证外联组合 25 = 115，按 min(100) 封顶为 100。
     */
    @Test
    public void scoring_combines_categories_once_and_caps_at_100() {
        ScanSettings settings = ScanSettings.builder()
                .mode(SecurityMode.WARN)
                .path(false)
                .network(true)
                .unicode(false)
                .toolRisk(false)
                .tracking(false)
                .build();
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "AKIA1234567890ABCDEF https://webhook.site/demo", settings));
        log.info("组合评分结果: {}", JSON.toJSONString(result));

        assertEquals(100, result.getScore());
        assertEquals("Critical", result.getLevel().name().charAt(0)
                + result.getLevel().name().substring(1).toLowerCase());
        assertEquals(SecurityAction.ALLOW, result.getFinalAction());
    }

    /**
     * 单条 finding 的 evidenceMasked 为打码证据：前 8 + **** + 后 4；
     * 审计日志 JSON 不再包含证据原文。
     */
    @Test
    public void evidence_is_masked_in_findings_and_serialized_json() {
        String secret = "sk-proj-abcdefghijklmnopqrstuvwx";
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "{\"content\":\"" + secret + "\"}", SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("打码结果: {}", JSON.toJSONString(result));

        var finding = result.getFindings().stream()
                .filter(f -> "credential_apikey_openai".equals(f.getRuleId()))
                .findFirst().orElseThrow();
        assertNotNull(finding.getEvidenceMasked());
        assertFalse(finding.getEvidenceMasked().equals(secret));
        assertTrue("前 8 字符保留", finding.getEvidenceMasked().startsWith("sk-proj-"));
        assertTrue("后 4 字符保留", finding.getEvidenceMasked().endsWith("uvwx"));
        assertTrue(finding.getEvidenceMasked().contains("****"));
        String serialized = JSON.toJSONString(result);
        assertFalse("序列化结果不得包含证据原文", serialized.contains("abcdefghijklmnopqrstuvwx"));
    }

    /**
     * 长度 ≤16 的短证据原样返回（打码豁免），如 15 字符的内网 IP。
     */
    @Test
    public void short_evidence_is_left_plain() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "169.254.169.254", SecurityTestSupport.networkOnly(SecurityMode.WARN)));
        log.info("短证据结果: {}", JSON.toJSONString(result));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "169.254.169.254".equals(f.getEvidenceMasked())));
    }

    /**
     * findings 上限 80 兜底：恶意构造的超大量 finding 被截断并标注 truncated。
     */
    @Test
    public void findings_are_capped_at_max_findings() {
        String body = JSON.toJSONString(IntStream.range(0, 100)
                .mapToObj(i -> "sk-proj-" + repeat((i + 97) % 26 + 'a', 24))
                .collect(Collectors.toList()));
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("findings 上限结果: {}", JSON.toJSONString(result));
        assertTrue(result.getFindings().size() <= 80);
        assertTrue(result.getSummary().contains("truncated=true"));
    }

    /**
     * blockCritical 在 audit 档也强制阻断 Critical，并写入 blockedReason=summary。
     */
    @Test
    public void block_critical_forces_block_and_records_reason() {
        ScanSettings settings = SecurityTestSupport.credentialOnly(SecurityMode.AUDIT)
                .toBuilder().blockCritical(true).build();
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "AKIA1234567890ABCDEF", settings));
        log.info("blockCritical 结果: {}", JSON.toJSONString(result));

        assertEquals(SecurityAction.BLOCK, result.getFinalAction());
        assertTrue(result.isBlocked());
        assertNotNull(result.getBlockedReason());
        assertEquals(result.getSummary(), result.getBlockedReason());
        assertFalse(result.isSanitized());
    }

    /**
     * REDACT 档命中可脱敏 High 时产出 SANITIZE，并标记 sanitized=true。
     */
    @Test
    public void redact_mode_marks_sanitized() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "sk-proj-abcdefghijklmnopqrstuvwx", SecurityTestSupport.credentialOnly(SecurityMode.REDACT)));
        log.info("REDACT 脱敏结果: {}", JSON.toJSONString(result));

        assertEquals(SecurityAction.SANITIZE, result.getFinalAction());
        assertTrue(result.isSanitized());
        assertFalse(result.isBlocked());
    }

    /**
     * 深度超限在 summary 标注 truncated。
     */
    @Test
    public void depth_limit_marks_summary_truncated() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            body.append("{\"a\":");
        }
        body.append("\"clean\"");
        body.append("}".repeat(20));

        AuditResult result = securityScanner.scan(ScanRequest.of(
                body.toString(), SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("深度截断结果: {}", JSON.toJSONString(result));
        assertTrue(result.getSummary().contains("truncated=true"));
    }

    /**
     * 节点数超过 5000 时停止遍历并标注 truncated。
     */
    @Test
    public void node_limit_marks_summary_truncated() {
        String body = JSON.toJSONString(Collections.nCopies(5001, 0));
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("节点截断结果: {}", JSON.toJSONString(result));
        assertTrue(result.getSummary().contains("truncated=true"));
    }

    /**
     * 配置关闭时不扫描，直接 Clean。
     */
    @Test
    public void disabled_scanner_returns_clean() {
        ScanSettings disabled = SecurityTestSupport.credentialOnly(SecurityMode.WARN)
                .toBuilder().enabled(false).build();
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "AKIA1234567890ABCDEF", disabled));
        log.info("关闭扫描结果: {}", JSON.toJSONString(result));
        assertEquals(0, result.getScore());
        assertTrue(result.getFindings().isEmpty());
        assertEquals(SecurityAction.ALLOW, result.getFinalAction());
        assertFalse(result.isBlocked());
        assertFalse(result.isSanitized());
    }

    /**
     * RESPONSE 默认不扫描（scanResponse=false 时 clean）。
     */
    @Test
    public void response_phase_is_clean_by_default() {
        ScanRequest request = ScanRequest.builder()
                .body("AKIA1234567890ABCDEF")
                .phase(ScanPhase.RESPONSE)
                .settings(SecurityTestSupport.credentialOnly(SecurityMode.BLOCK))
                .build();
        AuditResult result = securityScanner.scan(request);
        log.info("RESPONSE 默认结果: {}", JSON.toJSONString(result));
        assertEquals(ScanPhase.RESPONSE, result.getPhase());
        assertTrue(result.getFindings().isEmpty());
        assertEquals(SecurityAction.ALLOW, result.getFinalAction());
    }

    /**
     * 开启 scanResponse 后，响应体同样过扫描引擎（响应也可能带出训练数据中的密钥）。
     */
    @Test
    public void response_scanning_is_enabled_by_switch() {
        ScanSettings settings = SecurityTestSupport.credentialOnly(SecurityMode.WARN)
                .toBuilder().scanResponse(true).build();
        ScanRequest request = ScanRequest.builder()
                .body("AKIA1234567890ABCDEF")
                .phase(ScanPhase.RESPONSE)
                .settings(settings)
                .build();
        AuditResult result = securityScanner.scan(request);
        log.info("RESPONSE 开启结果: {}", JSON.toJSONString(result));
        assertEquals(ScanPhase.RESPONSE, result.getPhase());
        assertFalse("响应体应被扫描", result.getFindings().isEmpty());
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "credential_aws_access".equals(f.getRuleId())));
    }

    /**
     * max_scan_bytes 单字段性能保护：超长字段不进入扫描。
     */
    @Test
    public void max_scan_bytes_skips_oversized_fields() {
        ScanSettings settings = SecurityTestSupport.credentialOnly(SecurityMode.WARN)
                .toBuilder().maxScanBytes(8).build();
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "{\"content\":\"AKIA1234567890ABCDEF\"}", settings));
        log.info("maxScanBytes 结果: {}", JSON.toJSONString(result));
        assertEquals(0, result.getScore());
        assertTrue(result.getFindings().isEmpty());
    }

    private static String repeat(int ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append((char) ch);
        }
        return sb.toString();
    }
}
