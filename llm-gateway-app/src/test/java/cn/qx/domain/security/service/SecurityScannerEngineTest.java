package cn.qx.domain.security.service;

import cn.qx.domain.security.SecurityTestSupport;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SecurityScanner 引擎端口测试：JSON 钻取、原文兜底、边界、评分与策略。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityScannerEngineTest {

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
     * Critical 基础分 90 + 凭证外联组合 25 = 115，不随发现数量线性叠加。
     */
    @Test
    public void scoring_combines_categories_once() {
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

        assertEquals(115, result.getScore());
        assertEquals("Critical", result.getLevel().name().charAt(0)
                + result.getLevel().name().substring(1).toLowerCase());
        assertEquals(SecurityAction.ALLOW, result.getFinalAction());
    }

    /**
     * blockCritical 在 audit 档也强制阻断 Critical。
     */
    @Test
    public void block_critical_forces_block_in_any_mode() {
        ScanSettings settings = SecurityTestSupport.credentialOnly(SecurityMode.AUDIT)
                .toBuilder().blockCritical(true).build();
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "AKIA1234567890ABCDEF", settings));
        log.info("blockCritical 结果: {}", JSON.toJSONString(result));

        assertEquals(SecurityAction.BLOCK, result.getFinalAction());
        assertTrue(result.isBlocked());
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
    }
}
