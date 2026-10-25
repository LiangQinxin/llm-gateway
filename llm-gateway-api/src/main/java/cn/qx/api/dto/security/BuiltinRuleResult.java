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

    /** 内置规则记录 ID（UUID） */
    private String id;
    /** 规则标识（rule_id，与子扫描器代码 id 一致） */
    private String ruleId;
    /** 风险类别标签（凭证/敏感文件/隐写/命令/外联/追踪） */
    private String category;
    /** 规则名称 */
    private String name;
    /** 风险严重度 */
    private SecuritySeverity severity;
    /** 规则级命中动作（warn/redact/block）；空=跟随全局 mode */
    private String action;
    /** 规则标题 */
    private String title;
    /** 规则描述 */
    private String description;
    /** 关联全局类别开关的键（空=无类别开关） */
    private String toggleKey;
    /** 规则级启停（true=启用） */
    private boolean enabled;
    /** 创建时间（ISO-8601 带偏移） */
    private String createdAt;
    /** 更新时间（ISO-8601 带偏移） */
    private String updatedAt;

    public static BuiltinRuleResult from(RuleMetadata rule) {
        return BuiltinRuleResult.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .category(rule.getCategory())
                .name(rule.getName())
                .severity(rule.getSeverity())
                .action(rule.getAction())
                .title(rule.getTitle())
                .description(rule.getDescription())
                .toggleKey(rule.getToggleKey())
                .enabled(rule.isEnabled())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
