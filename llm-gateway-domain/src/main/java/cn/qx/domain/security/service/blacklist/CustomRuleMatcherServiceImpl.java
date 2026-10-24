package cn.qx.domain.security.service.blacklist;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanText;
import cn.qx.domain.security.service.rule.CustomRuleMeta;
import cn.qx.domain.security.service.rule.ISecurityRuleCacheService;
import cn.qx.domain.security.service.scanner.EvidenceMasker;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自定义黑白名单匹配：白名单按扫描器类别豁免，黑名单产生 custom finding。
 */
@Component
public class CustomRuleMatcherServiceImpl implements ICustomRuleMatcherService {

    private final ISecurityRuleCacheService ruleCache;

    public CustomRuleMatcherServiceImpl(ISecurityRuleCacheService ruleCache) {
        this.ruleCache = ruleCache;
    }

    @Override
    public boolean isWhitelisted(String scannerName, String text) {
        String category = whitelistCategory(scannerName);
        if (category == null || text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return ruleCache.listCustom("whitelist").stream()
                .filter(CustomRuleMeta::isEnabled)
                .filter(rule -> category.equalsIgnoreCase(rule.getCategory()))
                .map(CustomRuleMeta::getPattern)
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .anyMatch(pattern -> lower.contains(pattern.toLowerCase(Locale.ROOT)));
    }

    @Override
    public List<ScanFinding> customFindings(ScanText text) {
        if (text == null || text.text() == null || text.text().isEmpty()) {
            return List.of();
        }
        String lower = text.text().toLowerCase(Locale.ROOT);
        List<ScanFinding> findings = new ArrayList<>();
        for (CustomRuleMeta rule : ruleCache.listCustom("blacklist")) {
            if (!rule.isEnabled() || rule.getPattern() == null || rule.getPattern().isBlank()) {
                continue;
            }
            String pattern = rule.getPattern();
            int index = lower.indexOf(pattern.toLowerCase(Locale.ROOT));
            if (index < 0) {
                continue;
            }
            int end = Math.min(text.text().length(), index + pattern.length());
            String evidence = text.text().substring(index, end);
            findings.add(ScanFinding.builder()
                    .ruleId("custom.blacklist." + rule.getCategory())
                    .scanner("custom")
                    .categories(List.of())
                    .severity(rule.getSeverity())
                    .title(rule.getName())
                    .description(rule.getDescription())
                    .evidence(evidence)
                    .evidenceMasked(EvidenceMasker.mask(evidence))
                    .location(text.locationAt(index, end))
                    .message("Custom blacklist pattern matched")
                    .redactable(false)
                    .build());
        }
        return findings;
    }

    private static String whitelistCategory(String scannerName) {
        return switch (scannerName == null ? "" : scannerName) {
            case "network" -> "domain";
            case "path" -> "path";
            case "credential" -> "keyword";
            case "tool" -> "tool";
            default -> null;
        };
    }
}
