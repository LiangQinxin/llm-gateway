package cn.qx.api.dto.security;

import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.RuleMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内置规则管理面输出对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuiltinRuleResult {

    private String id;
    private String ruleId;
    private String category;
    private String name;
    private SecuritySeverity severity;
    private String title;
    private String description;
    private String toggleKey;
    private boolean enabled;
    private String createdAt;
    private String updatedAt;

    public static BuiltinRuleResult from(RuleMetadata rule) {
        return BuiltinRuleResult.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .category(rule.getCategory())
                .name(rule.getName())
                .severity(rule.getSeverity())
                .title(rule.getTitle())
                .description(rule.getDescription())
                .toggleKey(rule.getToggleKey())
                .enabled(rule.isEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
