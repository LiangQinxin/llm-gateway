package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.ISecurityScanner;
import cn.qx.domain.security.SecurityTestSupport;
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
 * CredentialScanner 端口测试：真实 Spring 容器装配，经 ISecurityScanner 断言外部结果。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class CredentialScannerTest {

    /** 被测的安全扫描引擎：仅开启凭证类别，经其断言 CredentialScanner 的命中。 */
    @Resource
    private ISecurityScanner securityScanner;

    /**
     * 已知前缀密钥与私钥头命中；严重度与规则 ID 符合收敛规则表。
     */
    @Test
    public void known_prefix_credentials_produce_expected_findings() {
        String body = """
                sk-proj-abcdefghijklmnopqrstuvwx
                AKIA1234567890ABCDEF
                ghp_abcdefghijklmnopqrstuvwxyz0123456789
                -----BEGIN OPENSSH PRIVATE KEY-----
                """;
        AuditResult result = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("凭证扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(rules.contains("credential_apikey_openai"));
        assertTrue(rules.contains("credential_aws_access"));
        assertTrue(rules.contains("credential_github_pat"));
        assertTrue(rules.contains("credential_private_key"));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getSeverity() == SecuritySeverity.CRITICAL));
        assertTrue("凭证规则应全部可打码", result.getFindings().stream()
                .allMatch(ScanFinding::isRedactable));
        assertEquals("高严重度在 warn 档仅记录", "Allow", result.getFinalAction().code());
    }

    /**
     * Anthropic/GitHub OAuth/Bearer 前缀与敏感字段名规则命中（对齐安全审计引擎前缀启发式）。
     */
    @Test
    public void anthropic_oauth_bearer_and_named_fields_are_detected() {
        String body = """
                sk-ant-api03-abcdefghijklmnopqrstuvwxyz
                gho_abcdefghijklmnopqrstuvwxyz0123456789
                Bearer abcdefghijklmnopqrstuvwxyz012345
                authorization: Bearer abcdef
                sessionid=abc12345 database_url=mysql://db
                """;
        AuditResult result = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("前缀与字段名扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue("sk-ant- 应命中", rules.contains("credential_apikey_anthropic"));
        assertTrue("gho_ 应命中", rules.contains("credential_github_oauth"));
        assertTrue("Bearer 应命中", rules.contains("credential_bearer"));
        assertTrue("敏感字段名应命中", rules.contains("credential_named_field"));
    }

    /**
     * 中文紧贴密钥（无空格分隔）也必须命中——
     * 旧版正则用 \b 锚定，Java \b 把中文字符视为 word 字符，
     * 导致"我的 sk-ant-xxx 这个是什么"这种中文 prompt 场景全部漏报。
     */
    @Test
    public void credentials_adjacent_to_chinese_are_detected() {
        String body = """
                我的 sk-ant-api03-abcdefghijklmnopqrstuvwxyz 这个是什么
                密钥 sk-proj-abcdefghijklmnopqrstuvwx 泄露了
                前缀AKIA1234567890ABCDEF后缀
                """;
        AuditResult result = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                body, SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("中文紧贴凭证扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue("中文紧贴 sk-ant- 应命中",
                rules.contains("credential_apikey_anthropic"));
        assertTrue("中文紧贴 sk-proj- 应命中",
                rules.contains("credential_apikey_openai"));
        assertTrue("中文紧贴 AKIA 应命中",
                rules.contains("credential_aws_access"));
    }

    /**
     * 键值对值长度 ≥8 且字母数字混合升级 High；示例值不命中。
     */
    @Test
    public void assignment_value_heuristic_and_exclusions() {
        AuditResult strong = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                "database.yml password=abc12345", SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        AuditResult excluded = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                "password=*** token=your-token max_tokens=4096 prompt_tokens=128",
                SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        AuditResult exactOnly = securityScanner.scan(cn.qx.domain.security.model.valobj.ScanRequest.of(
                "password=contest12345", SecurityTestSupport.credentialOnly(SecurityMode.WARN)));
        log.info("赋值凭证强值: {}", JSON.toJSONString(strong));
        log.info("赋值凭证排除值: {}", JSON.toJSONString(excluded));
        log.info("排除词精确匹配: {}", JSON.toJSONString(exactOnly));

        assertTrue(strong.getFindings().stream()
                .anyMatch(f -> "credential_password".equals(f.getRuleId())
                        && f.getSeverity() == SecuritySeverity.HIGH));
        assertTrue("占位值不应命中", excluded.getFindings().isEmpty());
        assertTrue("只在完整值等于排除词时抑制", exactOnly.getFindings().stream()
                .anyMatch(f -> "credential_password".equals(f.getRuleId())));
    }
}
