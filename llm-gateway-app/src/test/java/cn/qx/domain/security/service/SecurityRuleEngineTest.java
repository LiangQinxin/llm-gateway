package cn.qx.domain.security.service;

import cn.qx.api.ISecurityRuleCase;
import cn.qx.api.dto.security.BuiltinRuleUpdateRequest;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.api.dto.security.CustomRuleUpdateRequest;
import cn.qx.domain.security.adapter.repository.IBuiltinRuleRepository;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.RuleMetadata;
import cn.qx.domain.security.service.rule.SecurityRuleCatalog;
import cn.qx.infrastructure.adapter.repository.RuleSeedService;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 规则元数据驱动扫描引擎的行为测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class SecurityRuleEngineTest {

    /** 被测的安全扫描引擎：在调整规则元数据后断言其扫描结果。 */
    @Resource
    private ISecurityScanner securityScanner;

    /** 安全规则用例：更新内置规则开关/严重度，增删自定义黑白名单规则。 */
    @Resource
    private ISecurityRuleCase securityRuleCase;

    /** 内置规则仓储：读取与删除规则元数据，以断言播种与覆盖行为。 */
    @Resource
    private IBuiltinRuleRepository builtinRuleRepository;

    /** 规则播种服务：触发幂等播种，验证不覆盖用户修改、并补插缺失内置规则。 */
    @Resource
    private RuleSeedService ruleSeedService;

    /**
     * 表内 enabled 可关闭单条规则，severity 可覆盖代码默认值。
     */
    @Test
    public void builtin_metadata_controls_enabled_and_severity() {
        RuleMetadata original = builtinRuleRepository.getByRuleId("credential_apikey_openai");
        try {
            BuiltinRuleUpdateRequest disable = builtinRequest(original);
            disable.setEnabled(false);
            securityRuleCase.updateBuiltin(original.getRuleId(), disable);
            AuditResult disabled = securityScanner.scan(ScanRequest.of(
                    "sk-proj-abcdefghijklmnopqrstuvwx",
                    ScanSettings.builder().mode(SecurityMode.WARN).path(false).build()));
            log.info("禁用规则扫描结果: {}", JSON.toJSONString(disabled));
            assertFalse(disabled.getFindings().stream()
                    .anyMatch(f -> "credential_apikey_openai".equals(f.getRuleId())));

            BuiltinRuleUpdateRequest override = builtinRequest(original);
            override.setEnabled(true);
            override.setSeverity("low");
            securityRuleCase.updateBuiltin(original.getRuleId(), override);
            AuditResult overridden = securityScanner.scan(ScanRequest.of(
                    "sk-proj-abcdefghijklmnopqrstuvwx",
                    ScanSettings.builder().mode(SecurityMode.WARN).path(false).build()));
            log.info("覆盖严重度扫描结果: {}", JSON.toJSONString(overridden));
            assertTrue(overridden.getFindings().stream()
                    .anyMatch(f -> "credential_apikey_openai".equals(f.getRuleId())
                            && f.getSeverity() == SecuritySeverity.LOW));
        } finally {
            securityRuleCase.updateBuiltin(
                    original.getRuleId(), builtinRequest(original));
        }
    }

    /**
     * 内置规则 action 覆盖全局 mode 与 blockCritical；留空时回落到原矩阵。
     */
    @Test
    public void builtin_rule_action_overrides_global_mode() {
        RuleMetadata original = builtinRuleRepository.getByRuleId("credential_apikey_openai");
        String secret = "sk-proj-abcdefghijklmnopqrstuvwx";
        try {
            securityRuleCase.updateBuiltin(original.getRuleId(),
                    actionRequest(original, "block"));
            AuditResult blocked = securityScanner.scan(ScanRequest.of(secret,
                    ScanSettings.builder().mode(SecurityMode.WARN).path(false).build()));
            log.info("内置规则 block 覆盖结果: {}", JSON.toJSONString(blocked));
            assertFindingAction(blocked, "credential_apikey_openai", FindingAction.BLOCK, false);
            assertEquals(SecurityAction.BLOCK, blocked.getFinalAction());

            securityRuleCase.updateBuiltin(original.getRuleId(),
                    actionRequest(original, "redact"));
            AuditResult redacted = securityScanner.scan(ScanRequest.of(secret,
                    ScanSettings.builder().mode(SecurityMode.AUDIT).path(false).build()));
            log.info("内置规则 redact 覆盖结果: {}", JSON.toJSONString(redacted));
            assertFindingAction(redacted, "credential_apikey_openai", FindingAction.REDACT, true);
            assertEquals(SecurityAction.SANITIZE, redacted.getFinalAction());

            securityRuleCase.updateBuiltin(original.getRuleId(),
                    actionRequest(original, "warn"));
            AuditResult warned = securityScanner.scan(ScanRequest.of(secret,
                    ScanSettings.builder()
                            .mode(SecurityMode.BLOCK)
                            .blockCritical(true)
                            .path(false)
                            .build()));
            log.info("内置规则 warn 覆盖结果: {}", JSON.toJSONString(warned));
            assertFindingAction(warned, "credential_apikey_openai", FindingAction.WARN, false);
            assertEquals(SecurityAction.ALLOW, warned.getFinalAction());

            BuiltinRuleUpdateRequest blank = actionRequest(original, "");
            securityRuleCase.updateBuiltin(original.getRuleId(), blank);
            AuditResult fallback = securityScanner.scan(ScanRequest.of(secret,
                    ScanSettings.builder().mode(SecurityMode.WARN).path(false).build()));
            log.info("内置规则 action 留空回落矩阵: {}", JSON.toJSONString(fallback));
            assertFindingAction(fallback, "credential_apikey_openai", FindingAction.WARN, true);
            assertEquals(SecurityAction.ALLOW, fallback.getFinalAction());
        } finally {
            securityRuleCase.updateBuiltin(original.getRuleId(), builtinRequest(original));
        }
    }

    /**
     * 白名单只豁免映射类别；域名白名单不能放过同段文本中的凭证。
     */
    @Test
    public void whitelist_is_scoped_to_one_scanner_category() {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String whitelistId = null;
        try {
            whitelistId = securityRuleCase.createCustom(
                    customRequest("whitelist", "domain",
                            "test-domain-" + suffix, "webhook.site")).getId();

            AuditResult result = securityScanner.scan(ScanRequest.of(
                    "AKIA1234567890ABCDEF https://webhook.site/demo",
                    ScanSettings.builder()
                            .mode(SecurityMode.WARN)
                            .path(false)
                            .network(true)
                            .build()));
            log.info("类别白名单扫描结果: {}", JSON.toJSONString(result));
            assertTrue("凭证仍应命中", result.getFindings().stream()
                    .anyMatch(f -> "credential_aws_access".equals(f.getRuleId())));
            assertFalse("网络类别应被豁免", result.getFindings().stream()
                    .anyMatch(f -> "network_exfil_target".equals(f.getRuleId())));
        } finally {
            if (whitelistId != null) {
                securityRuleCase.deleteCustom(whitelistId);
            }
        }
    }

    /**
     * path 白名单只豁免 PathScanner，示例路径上下文可正常工作。
     */
    @Test
    public void path_whitelist_exempts_sensitive_file_rule() {
        String id = null;
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            CustomRuleRequest request = customRequest(
                    "whitelist", "path", "safe-path-" + suffix, "/safe-dir");
            request.setSeverity("medium");
            request.setAction("warn");
            id = securityRuleCase.createCustom(request).getId();

            AuditResult result = securityScanner.scan(ScanRequest.of(
                    "open /safe-dir then read .env",
                    ScanSettings.builder()
                            .mode(SecurityMode.WARN)
                            .credential(false)
                            .path(true)
                            .build()));
            log.info("路径白名单扫描结果: {}", JSON.toJSONString(result));
            assertFalse(result.getFindings().stream()
                    .anyMatch(f -> "path_sensitive_project".equals(f.getRuleId())));
        } finally {
            if (id != null) {
                securityRuleCase.deleteCustom(id);
            }
        }
    }

    /**
     * 自定义黑名单命中后产生 custom finding，并参与统一评分。
     */
    @Test
    public void blacklist_adds_custom_finding() {
        String id = null;
        String marker = "blocked-marker-" + java.util.UUID.randomUUID();
        try {
            id = securityRuleCase.createCustom(
                    customRequest("blacklist", "keyword", "test-blacklist", marker)).getId();

            AuditResult result = securityScanner.scan(ScanRequest.of(
                    "{\"content\":\"" + marker + "\"}",
                    ScanSettings.builder()
                            .mode(SecurityMode.BLOCK)
                            .credential(false)
                            .path(false)
                            .build()));
            log.info("黑名单扫描结果: {}", JSON.toJSONString(result));
            assertTrue(result.getFindings().stream()
                    .anyMatch(f -> f.getRuleId().equals("custom.blacklist.keyword")
                            && f.getSeverity() == SecuritySeverity.HIGH));
        } finally {
            if (id != null) {
                securityRuleCase.deleteCustom(id);
            }
        }
    }

    /**
     * 自定义黑名单 action 同样覆盖全局 mode，并且修改后缓存立即生效。
     */
    @Test
    public void custom_rule_action_overrides_global_mode_and_refreshes_cache() {
        String id = null;
        String marker = "action-marker-" + java.util.UUID.randomUUID();
        try {
            id = securityRuleCase.createCustom(
                    customRequest("blacklist", "keyword", "action-blacklist", marker, "block"))
                    .getId();

            AuditResult blocked = scanCustom(marker, SecurityMode.AUDIT);
            log.info("自定义规则 block 结果: {}", JSON.toJSONString(blocked));
            assertFindingAction(blocked, "custom.blacklist.keyword", FindingAction.BLOCK, false);
            assertEquals(SecurityAction.BLOCK, blocked.getFinalAction());

            CustomRuleUpdateRequest update = new CustomRuleUpdateRequest();
            update.setAction("redact");
            securityRuleCase.updateCustom(id, update);
            AuditResult redacted = scanCustom(marker, SecurityMode.AUDIT);
            log.info("自定义规则 redact 结果: {}", JSON.toJSONString(redacted));
            assertFindingAction(redacted, "custom.blacklist.keyword", FindingAction.REDACT, true);
            assertEquals(SecurityAction.SANITIZE, redacted.getFinalAction());

            update.setAction("warn");
            securityRuleCase.updateCustom(id, update);
            AuditResult warned = scanCustom(marker, SecurityMode.BLOCK);
            log.info("自定义规则 warn 覆盖 block 模式结果: {}", JSON.toJSONString(warned));
            assertFindingAction(warned, "custom.blacklist.keyword", FindingAction.WARN, false);
            assertEquals(SecurityAction.ALLOW, warned.getFinalAction());

            update.setAction("");
            securityRuleCase.updateCustom(id, update);
            AuditResult fallback = scanCustom(marker, SecurityMode.AUDIT);
            log.info("自定义规则 action 留空结果: {}", JSON.toJSONString(fallback));
            assertFindingAction(fallback, "custom.blacklist.keyword", FindingAction.ALLOW, false);
            assertEquals(SecurityAction.ALLOW, fallback.getFinalAction());
        } finally {
            if (id != null) {
                securityRuleCase.deleteCustom(id);
            }
        }
    }

    /**
     * 同 scanner/证据/位置命中多条规则时，规则动作优先于严重度：低严重度 block 不被高严重度 warn 覆盖。
     */
    @Test
    public void deduplication_prefers_stricter_rule_action() {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String pattern = "dedupe-marker-" + suffix;
        String blockId = null;
        String warnId = null;
        try {
            CustomRuleRequest block = customRequest(
                    "blacklist", "keyword", "dedupe-block", pattern, "block");
            block.setSeverity("low");
            blockId = securityRuleCase.createCustom(block).getId();

            CustomRuleRequest warn = customRequest(
                    "blacklist", "keyword", "dedupe-warn", pattern, "warn");
            warn.setSeverity("critical");
            warnId = securityRuleCase.createCustom(warn).getId();

            AuditResult result = scanCustom(pattern, SecurityMode.AUDIT);
            log.info("同位置动作去重结果: {}", JSON.toJSONString(result));
            assertTrue(result.getFindings().stream()
                    .filter(finding -> "custom.blacklist.keyword".equals(finding.getRuleId()))
                    .allMatch(finding -> finding.getAction() == FindingAction.BLOCK));
            assertEquals(SecurityAction.BLOCK, result.getFinalAction());
        } finally {
            if (blockId != null) {
                securityRuleCase.deleteCustom(blockId);
            }
            if (warnId != null) {
                securityRuleCase.deleteCustom(warnId);
            }
        }
    }

    /**
     * 动态凭证规则只升不降：表值 MEDIUM 不能压制启发式 HIGH。
     */
    @Test
    public void dynamic_credential_severity_can_only_increase() {
        RuleMetadata original = builtinRuleRepository.getByRuleId("credential_password");
        try {
            BuiltinRuleUpdateRequest update = builtinRequest(original);
            update.setSeverity("medium");
            securityRuleCase.updateBuiltin(original.getRuleId(), update);
            AuditResult result = securityScanner.scan(ScanRequest.of(
                    "{\"password\":\"aB3dE5fH7jK9\"}",
                    ScanSettings.builder().mode(SecurityMode.WARN).path(false).build()));
            log.info("动态 severity 结果: {}", JSON.toJSONString(result));
            assertTrue(result.getFindings().stream()
                    .anyMatch(f -> "credential_password".equals(f.getRuleId())
                            && f.getSeverity() == SecuritySeverity.HIGH));
        } finally {
            securityRuleCase.updateBuiltin(
                    original.getRuleId(), builtinRequest(original));
        }
    }

    /**
     * toggle_key 是第二层开关：规则 enabled 但类别关闭时不生效。
     */
    @Test
    public void toggle_key_gates_enabled_rule() {
        AuditResult disabled = securityScanner.scan(ScanRequest.of(
                "hello\u200Bworld",
                ScanSettings.builder()
                        .mode(SecurityMode.WARN)
                        .credential(false)
                        .path(false)
                        .unicode(false)
                        .build()));
        AuditResult enabled = securityScanner.scan(ScanRequest.of(
                "hello\u200Bworld",
                ScanSettings.builder()
                        .mode(SecurityMode.WARN)
                        .credential(false)
                        .path(false)
                        .unicode(true)
                        .build()));
        log.info("toggle_key 关闭: {}, 开启: {}",
                JSON.toJSONString(disabled), JSON.toJSONString(enabled));
        assertFalse(disabled.getFindings().stream()
                .anyMatch(f -> "unicode_zero_width".equals(f.getRuleId())));
        assertTrue(enabled.getFindings().stream()
                .anyMatch(f -> "unicode_zero_width".equals(f.getRuleId())));
    }

    /**
     * 幂等播种不覆盖用户修改后的 severity。
     */
    @Test
    public void seed_is_idempotent_and_does_not_overwrite_user_changes() {
        RuleMetadata original = builtinRuleRepository.getByRuleId("credential_apikey_openai");
        try {
            BuiltinRuleUpdateRequest update = builtinRequest(original);
            update.setSeverity("low");
            securityRuleCase.updateBuiltin(original.getRuleId(), update);
            ruleSeedService.seed();
            RuleMetadata persisted = builtinRuleRepository.getByRuleId("credential_apikey_openai");
            log.info("重复播种后的规则: {}", JSON.toJSONString(persisted));
            assertEquals(SecuritySeverity.LOW, persisted.getSeverity());
            assertEquals(65, SecurityRuleCatalog.defaults().size());
        } finally {
            securityRuleCase.updateBuiltin(
                    original.getRuleId(), builtinRequest(original));
        }
    }

    /**
     * 被删除的内置规则在下次幂等播种时补插回来。
     */
    @Test
    public void seed_restores_missing_builtin_rule() {
        String ruleId = "credential_apikey_openai";
        builtinRuleRepository.deleteByRuleId(ruleId);
        assertNull(builtinRuleRepository.getByRuleId(ruleId));

        ruleSeedService.seed();

        RuleMetadata restored = builtinRuleRepository.getByRuleId(ruleId);
        log.info("补插后的内置规则: {}", JSON.toJSONString(restored));
        assertNotNull(restored);
    }

    private static BuiltinRuleUpdateRequest builtinRequest(RuleMetadata rule) {
        BuiltinRuleUpdateRequest request = new BuiltinRuleUpdateRequest();
        request.setSeverity(rule.getSeverity().name().toLowerCase());
        request.setEnabled(rule.isEnabled());
        request.setTitle(rule.getTitle());
        request.setDescription(rule.getDescription());
        request.setAction(rule.getAction() == null ? "" : rule.getAction());
        return request;
    }

    private static BuiltinRuleUpdateRequest actionRequest(RuleMetadata rule, String action) {
        BuiltinRuleUpdateRequest request = builtinRequest(rule);
        request.setAction(action);
        return request;
    }

    private static CustomRuleRequest customRequest(String ruleType,
                                                   String category,
                                                   String name,
                                                   String pattern) {
        return customRequest(ruleType, category, name, pattern, "block");
    }

    private static CustomRuleRequest customRequest(String ruleType,
                                                   String category,
                                                   String name,
                                                   String pattern,
                                                   String action) {
        CustomRuleRequest request = new CustomRuleRequest();
        request.setRuleType(ruleType);
        request.setCategory(category);
        request.setName(name);
        request.setPattern(pattern);
        request.setSeverity("high");
        request.setAction(action);
        request.setEnabled(true);
        request.setDescription("test");
        return request;
    }

    private AuditResult scanCustom(String marker, SecurityMode mode) {
        return securityScanner.scan(ScanRequest.of(
                "{\"content\":\"" + marker + "\"}",
                ScanSettings.builder()
                        .mode(mode)
                        .credential(false)
                        .path(false)
                        .build()));
    }

    private static void assertFindingAction(AuditResult result,
                                            String ruleId,
                                            FindingAction action,
                                            boolean redactable) {
        var finding = result.getFindings().stream()
                .filter(item -> ruleId.equals(item.getRuleId()))
                .findFirst().orElseThrow();
        assertEquals(action, finding.getAction());
        assertEquals(redactable, finding.isRedactable());
    }
}
