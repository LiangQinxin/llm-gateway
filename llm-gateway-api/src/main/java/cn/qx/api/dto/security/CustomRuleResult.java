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

    /** 自定义规则记录 ID（UUID） */
    private String id;
    /** 规则类型（blacklist|whitelist） */
    private String ruleType;
    /** 规则类别（domain|tool|path|keyword） */
    private String category;
    /** 规则名称 */
    private String name;
    /** 匹配模式（子串匹配，禁用正则） */
    private String pattern;
    /** 风险严重度 */
    private SecuritySeverity severity;
    /** 命中动作（warn|redact|block） */
    private String action;
    /** 是否启用（true=启用） */
    private boolean enabled;
    /** 规则描述 */
    private String description;
    /** 创建时间（ISO-8601 带偏移） */
    private String createdAt;
    /** 更新时间（ISO-8601 带偏移） */
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
