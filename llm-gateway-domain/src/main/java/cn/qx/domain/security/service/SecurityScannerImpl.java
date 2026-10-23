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
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全扫描引擎：JSON 值钻取 + 原文兜底 + 去重 + 三段评分 + 策略动作。
 */
@Slf4j
@Service
public class SecurityScannerImpl implements ISecurityScanner {

    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 5000;

    private final List<ISubScanner> scanners;
    private final ScanSettings defaultSettings;
    private final SecurityRiskScorer riskScorer;
    private final SecurityPolicy securityPolicy;

    public SecurityScannerImpl(List<ISubScanner> scanners,
                               ScanSettings defaultSettings,
                               SecurityRiskScorer riskScorer,
                               SecurityPolicy securityPolicy) {
        this.scanners = List.copyOf(scanners);
        this.defaultSettings = defaultSettings;
        this.riskScorer = riskScorer;
        this.securityPolicy = securityPolicy;
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
        if (phase == ScanPhase.RESPONSE) {
            return AuditResult.clean(phase, "clean: response scanning is not enabled");
        }

        TraversalState state = new TraversalState();
        List<ScanText> texts = new ArrayList<>();
        try {
            Object parsed = JSON.parse(body);
            traverse(parsed, "$", 0, state, texts);
        } catch (Exception ignored) {
            // 非 JSON 原文仍由后续 @raw 扫描兜底。
        }
        texts.add(new ScanText(body, "@raw", true));

        List<ISubScanner> enabledScanners = scanners.stream()
                .filter(scanner -> scanner.enabled(settings))
                .toList();
        List<ScanFinding> findings = new ArrayList<>();
        for (ScanText text : texts) {
            for (ISubScanner scanner : enabledScanners) {
                findings.addAll(scanner.scan(text));
            }
        }
        List<ScanFinding> deduplicated = deduplicate(findings);
        SecurityRiskScorer.RiskAssessment assessment = riskScorer.score(deduplicated);
        List<ScanFinding> actioned = applyActions(deduplicated, settings);
        boolean anyRedact = actioned.stream()
                .anyMatch(finding -> finding.getAction() == FindingAction.REDACT);
        SecurityAction finalAction = securityPolicy.globalAction(
                assessment.level(), anyRedact, settings.getMode(), settings.isBlockCritical());
        String summary = summary(actioned, assessment, state);
        log.info("安全扫描完成: phase={}, score={}, level={}, action={}, findings={}, summary={}",
                phase, assessment.score(), assessment.level(), finalAction,
                actioned.size(), summary);
        return AuditResult.builder()
                .phase(phase)
                .findings(List.copyOf(actioned))
                .score(assessment.score())
                .level(assessment.level())
                .finalAction(finalAction)
                .blocked(finalAction == SecurityAction.BLOCK)
                .summary(summary)
                .build();
    }

    private static void traverse(Object node,
                                 String path,
                                 int depth,
                                 TraversalState state,
                                 List<ScanText> texts) {
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
            texts.add(new ScanText(value, path, false));
            return;
        }
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                traverse(entry.getValue(), childPath(path, String.valueOf(entry.getKey())),
                        depth + 1, state, texts);
            }
            return;
        }
        if (node instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                traverse(item, path + "[" + index++ + "]", depth + 1, state, texts);
            }
        }
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
                                  TraversalState state) {
        String scanStats = "scanned " + state.strings + " string(s), truncated=" + state.truncated;
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
        private int nodes;
        private int strings;
        private boolean truncated;
        private boolean stopped;
    }
}
