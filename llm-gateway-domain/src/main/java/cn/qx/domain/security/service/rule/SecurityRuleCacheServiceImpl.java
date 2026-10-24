package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.adapter.repository.IBuiltinRuleRepository;
import cn.qx.domain.security.adapter.repository.ICustomRuleRepository;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则元数据与自定义规则进程内缓存；读取失败 fail-open 到代码出厂目录。
 */
@Slf4j
@Component
public class SecurityRuleCacheServiceImpl implements ISecurityRuleCacheService {

    /**
     * 这些规则由扫描器按命中上下文决定基础严重度，表值只能上调不能下调。
     */
    private static final Set<String> DYNAMIC_RULES = Set.of(
            "credential_password", "credential_token", "credential_secret",
            "network_internal_probe");

    private final IBuiltinRuleRepository builtinRuleRepository;
    private final ICustomRuleRepository customRuleRepository;
    private final boolean cacheEnabled;
    private volatile Snapshot snapshot;

    public SecurityRuleCacheServiceImpl(IBuiltinRuleRepository builtinRuleRepository,
                                        ICustomRuleRepository customRuleRepository,
                                        @Value("${security.rules.cache-enabled:true}") boolean cacheEnabled) {
        this.builtinRuleRepository = builtinRuleRepository;
        this.customRuleRepository = customRuleRepository;
        this.cacheEnabled = cacheEnabled;
    }

    /**
     * 全量刷新缓存；表异常时保留可用出厂规则，不阻断扫描。
     */
    @Override
    public synchronized void refresh() {
        Map<String, RuleMetadata> loadedBuiltin = defaults();
        List<CustomRuleMeta> loadedCustom = List.of();
        try {
            List<RuleMetadata> loaded = builtinRuleRepository.listAll();
            if (!loaded.isEmpty()) {
                Map<String, RuleMetadata> overrides = new LinkedHashMap<>();
                for (RuleMetadata rule : loaded) {
                    overrides.put(rule.getRuleId(), rule);
                }
                loadedBuiltin = overrides;
            }
            loadedCustom = List.copyOf(customRuleRepository.listAll());
        } catch (RuntimeException e) {
            log.error("安全规则缓存刷新失败，降级代码默认且不阻断请求: {}", e.getMessage());
            loadedBuiltin = defaults();
            loadedCustom = List.of();
        }
        snapshot = new Snapshot(loadedBuiltin, loadedCustom);
    }

    @Override
    public RuleMetadata findBuiltin(String ruleId) {
        return copy(current().builtin().get(ruleId));
    }

    @Override
    public List<RuleMetadata> listBuiltin() {
        return current().builtin().values().stream()
                .map(SecurityRuleCacheServiceImpl::copy)
                .toList();
    }

    @Override
    public List<CustomRuleMeta> listCustom() {
        return current().custom().stream()
                .map(SecurityRuleCacheServiceImpl::copy)
                .toList();
    }

    @Override
    public List<CustomRuleMeta> listCustom(String ruleType) {
        if (ruleType == null || ruleType.isBlank()) {
            return listCustom();
        }
        return listCustom().stream()
                .filter(rule -> ruleType.equalsIgnoreCase(rule.getRuleType()))
                .toList();
    }

    @Override
    public boolean isRuleEnabled(RuleMetadata rule, ScanSettings settings) {
        if (rule == null) {
            return true;
        }
        return rule.isEnabled() && toggleEnabled(rule.getToggleKey(), settings);
    }

    @Override
    public SecuritySeverity effectiveSeverity(ScanFinding finding, RuleMetadata rule) {
        if (rule == null || rule.getSeverity() == null) {
            return finding.getSeverity();
        }
        if (DYNAMIC_RULES.contains(finding.getRuleId())) {
            return finding.getSeverity() != null
                    && finding.getSeverity().ordinal() > rule.getSeverity().ordinal()
                    ? finding.getSeverity() : rule.getSeverity();
        }
        return rule.getSeverity();
    }

    private Snapshot current() {
        if (!cacheEnabled) {
            refresh();
        }
        Snapshot current = snapshot;
        if (current == null) {
            refresh();
            current = snapshot;
        }
        return current;
    }

    private static boolean toggleEnabled(String toggleKey, ScanSettings settings) {
        if (toggleKey == null || toggleKey.isBlank()) {
            return true;
        }
        return switch (toggleKey) {
            case "security.scan_unicode" -> settings != null && settings.isUnicode();
            case "security.scan_network" -> settings != null && settings.isNetwork();
            case "security.scan_tools" -> settings != null && settings.isToolRisk();
            case "security.scan_tracking" -> settings != null && settings.isTracking();
            default -> true;
        };
    }

    private static Map<String, RuleMetadata> defaults() {
        Map<String, RuleMetadata> defaults = new LinkedHashMap<>();
        for (RuleMetadata rule : SecurityRuleCatalog.defaults()) {
            defaults.put(rule.getRuleId(), rule);
        }
        return defaults;
    }

    private record Snapshot(Map<String, RuleMetadata> builtin,
                            List<CustomRuleMeta> custom) {
    }

    private static RuleMetadata copy(RuleMetadata source) {
        return source == null ? null : source.toBuilder().build();
    }

    private static CustomRuleMeta copy(CustomRuleMeta source) {
        return source.toBuilder().build();
    }
}
