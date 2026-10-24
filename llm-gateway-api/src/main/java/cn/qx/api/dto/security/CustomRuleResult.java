package cn.qx.api.dto.security;

import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.CustomRuleMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义规则管理面输出对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomRuleResult {

    private String id;
    private String ruleType;
    private String category;
    private String name;
    private String pattern;
    private SecuritySeverity severity;
    private String action;
    private boolean enabled;
    private String description;
    private String createdAt;
    private String updatedAt;

    public static CustomRuleResult from(CustomRuleMeta rule) {
        return CustomRuleResult.builder()
                .id(rule.getId())
                .ruleType(rule.getRuleType())
                .category(rule.getCategory())
                .name(rule.getName())
                .pattern(rule.getPattern())
                .severity(rule.getSeverity())
                .action(rule.getAction())
                .enabled(rule.isEnabled())
                .description(rule.getDescription())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
