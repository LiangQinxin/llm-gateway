package cn.qx.domain.security.service;

import cn.qx.domain.security.adapter.port.ISubScanner;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.ScanText;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.service.blacklist.ICustomRuleMatcherService;
import cn.qx.domain.security.service.rule.RuleMetadata;
import cn.qx.domain.security.service.rule.ISecurityRuleCacheService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全扫描引擎：JSON 值钻取 + 原文兜底 + 去重 + 三段评分 + 策略动作。
 * 对齐安全审计引擎：findings 上限 MAX_FINDINGS=80 兜底、max_scan_bytes
 * 单字段性能保护、请求/响应双侧开关、证据打码输出。
 */
@Slf4j
@Service
public class SecurityScannerImpl implements ISecurityScanner {

    /** JSON 递归钻取最大深度（超出即截断，防深层嵌套耗尽）。 */
    private static final int MAX_DEPTH = 16;
    /** JSON 钻取最大节点数（超出即停止，防超大对象耗尽）。 */
    private static final int MAX_NODES = 5000;
    /** 单次扫描发现条数上限（超出即截断，兜底保护）。 */
    private static final int MAX_FINDINGS = 80;

    /** 全部已注册子扫描器（按 Spring bean 注入，运行时按类别开关过滤启用）。 */
    private final List<ISubScanner> scanners;
    /** 未显式传入配置时使用的默认扫描设置。 */
    private final ScanSettings defaultSettings;
    /** 三段制风险评分器（基础分取最高 severity + 类别组合加分，封顶 100）。 */
    private final SecurityRiskScorer riskScorer;
    /** 策略档位→最终动作映射器。 */
    private final SecurityPolicy securityPolicy;
    /** 内置/自定义规则元数据缓存。 */
    private final ISecurityRuleCacheService ruleCache;
    /** 自定义黑白名单匹配器（白名单豁免、黑名单产生 custom finding）。 */
    private final ICustomRuleMatcherService customRuleMatcher;

    public SecurityScannerImpl(List<ISubScanner> scanners,
                               ScanSettings defaultSettings,
                               SecurityRiskScorer riskScorer,
                               SecurityPolicy securityPolicy,
                               ISecurityRuleCacheService ruleCache,
                               ICustomRuleMatcherService customRuleMatcher) {
        this.scanners = List.copyOf(scanners);
        this.defaultSettings = defaultSettings;
        this.riskScorer = riskScorer;
        this.securityPolicy = securityPolicy;
        this.ruleCache = ruleCache;
        this.customRuleMatcher = customRuleMatcher;
    }

    @Override
    public AuditResult scan(String body) {
        return scan(ScanRequest.of(body, defaultSettings));
    }

    @Override
    public AuditResult scan(ScanRequest request) {
        String body = request == null ? null : request.getBody();
        ScanPhase phase = request == null || request.getPhase() == null
                ? ScanPhase.REQUEST : request.getPhase();
        ScanSettings settings = request == null || request.getSettings() == null
                ? defaultSettings : request.getSettings();
        if (body == null || body.isBlank() || settings == null || !settings.isEnabled()) {
            return AuditResult.clean(phase, "clean: scan disabled or empty body");
        }
        if (phase == ScanPhase.REQUEST && !settings.isScanRequest()) {
            return AuditResult.clean(phase, "clean: request scanning is not enabled");
        }
        if (phase == ScanPhase.RESPONSE && !settings.isScanResponse()) {
            return AuditResult.clean(phase, "clean: response scanning is not enabled");
        }

        TraversalState state = new TraversalState();
        List<ScanText> texts = new ArrayList<>();
        try {
            Object parsed = JSON.parse(body);
            traverse(parsed, "$", 0, state, texts, settings.getMaxScanBytes());
        } catch (Exception ignored) {
            // 非 JSON 原文仍由后续 @raw 扫描兜底。
        }
        if (withinByteLimit(body, settings.getMaxScanBytes())) {
            texts.add(new ScanText(body, "@raw", true));
        }

        List<ISubScanner> enabledScanners = scanners.stream()
                .filter(scanner -> scanner.enabled(settings))
                .toList();
        List<ScanFinding> findings = new ArrayList<>();
        for (ScanText text : texts) {
            for (ISubScanner scanner : enabledScanners) {
                if (customRuleMatcher.isWhitelisted(scanner.scannerName(), text.text())) {
                    continue;
                }
                for (ScanFinding finding : scanner.scan(text)) {
                    addIfEnabled(findings, finding, settings);
                    if (findings.size() >= MAX_FINDINGS) {
                        break;
                    }
                }
                if (findings.size() >= MAX_FINDINGS) {
                    break;
                }
            }
            for (ScanFinding finding : customRuleMatcher.customFindings(text)) {
                findings.add(finding);
                if (findings.size() >= MAX_FINDINGS) {
                    break;
                }
            }
            if (findings.size() >= MAX_FINDINGS) {
                break;
            }
        }
        boolean findingsTruncated = findings.size() >= MAX_FINDINGS;
        if (findingsTruncated) {
            findings = new ArrayList<>(findings.subList(0, MAX_FINDINGS));
        }

        List<ScanFinding> deduplicated = deduplicate(findings);
        SecurityRiskScorer.RiskAssessment assessment = riskScorer.score(deduplicated);
        List<ScanFinding> actioned = applyActions(deduplicated, settings);
        boolean anyRedact = actioned.stream()
                .anyMatch(finding -> finding.getAction() == FindingAction.REDACT);
        SecurityAction finalAction = securityPolicy.globalAction(
                assessment.level(), anyRedact, settings.getMode(), settings.isBlockCritical());
        String summary = summary(actioned, assessment, state, findingsTruncated);
        log.info("安全扫描完成: phase={}, score={}, level={}, action={}, findings={}, truncated={}, summary={}",
                phase, assessment.score(), assessment.level(), finalAction,
                actioned.size(), findingsTruncated, summary);
        return AuditResult.builder()
                .phase(phase)
                .findings(List.copyOf(actioned))
                .score(assessment.score())
                .level(assessment.level())
                .finalAction(finalAction)
                .blocked(finalAction == SecurityAction.BLOCK)
                .blockedReason(finalAction == SecurityAction.BLOCK ? summary : null)
                .sanitized(finalAction == SecurityAction.SANITIZE)
                .summary(summary)
                .build();
    }

    private void addIfEnabled(List<ScanFinding> findings,
                              ScanFinding finding,
                              ScanSettings settings) {
        RuleMetadata metadata = ruleCache.findBuiltin(finding.getRuleId());
        if (!ruleCache.isRuleEnabled(metadata, settings)) {
            return;
        }
        findings.add(finding.toBuilder()
                .severity(ruleCache.effectiveSeverity(finding, metadata))
                .build());
    }

    private static void traverse(Object node,
                                 String path,
                                 int depth,
                                 TraversalState state,
                                 List<ScanText> texts,
                                 int maxScanBytes) {
        if (state.stopped) {
            return;
        }
        if (depth > MAX_DEPTH) {
            state.truncated = true;
            return;
        }
        state.nodes++;
        if (state.nodes > MAX_NODES) {
            state.truncated = true;
            state.stopped = true;
            return;
        }
        if (node instanceof String value) {
            state.strings++;
            if (withinByteLimit(value, maxScanBytes)) {
                texts.add(new ScanText(value, path, false));
            }
            return;
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                traverse(entry.getValue(), childPath(path, String.valueOf(entry.getKey())),
                        depth + 1, state, texts, maxScanBytes);
            }
            return;
        }
        if (node instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                traverse(item, path + "[" + index++ + "]", depth + 1, state, texts, maxScanBytes);
            }
        }
    }

    private static boolean withinByteLimit(String value, int maxScanBytes) {
        if (maxScanBytes <= 0) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= maxScanBytes;
    }

    private static String childPath(String parent, String key) {
        if (key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return parent + "." + key;
        }
        return parent + "['" + key.replace("'", "\\'") + "']";
    }

    private static List<ScanFinding> deduplicate(List<ScanFinding> findings) {
        Map<String, ScanFinding> selected = new LinkedHashMap<>();
        for (ScanFinding finding : findings) {
            String key = finding.getScanner() + "\u0000"
                    + finding.getEvidence() + "\u0000" + finding.getLocation();
            ScanFinding existing = selected.get(key);
            if (existing == null || finding.getSeverity().ordinal() > existing.getSeverity().ordinal()) {
                selected.put(key, finding);
            }
        }
        return new ArrayList<>(selected.values());
    }

    private List<ScanFinding> applyActions(List<ScanFinding> findings, ScanSettings settings) {
        return findings.stream()
                .map(finding -> finding.toBuilder()
                        .action(securityPolicy.actionFor(
                                finding.getSeverity(), finding.isRedactable(),
                                settings.getMode(), settings.isBlockCritical()))
                        .build())
                .toList();
    }

    private static String summary(List<ScanFinding> findings,
                                  SecurityRiskScorer.RiskAssessment assessment,
                                  TraversalState state,
                                  boolean findingsTruncated) {
        String scanStats = "scanned " + state.strings + " string(s), truncated="
                + (state.truncated || findingsTruncated);
        if (findings.isEmpty()) {
            return "clean: " + scanStats;
        }
        Map<String, Long> actionCounts = findings.stream()
                .collect(Collectors.groupingBy(
                        finding -> finding.getAction().code(), LinkedHashMap::new, Collectors.counting()));
        return assessment.level().name() + " risk (score " + assessment.score() + "): "
                + findings.size() + " finding(s), actions=" + actionCounts + ", " + scanStats;
    }

    private static final class TraversalState {
        /** JSON 钻取已访问节点计数（超 MAX_NODES 即截断）。 */
        private int nodes;
        /** 钻取到的字符串字段计数（用于 summary 统计）。 */
        private int strings;
        /** 钻取是否因深度/节点数超限被截断。 */
        private boolean truncated;
        /** 钻取是否因超限提前停止。 */
        private boolean stopped;
    }
}
