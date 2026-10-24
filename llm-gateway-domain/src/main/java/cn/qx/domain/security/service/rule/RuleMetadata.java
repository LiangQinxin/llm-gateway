package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.SecuritySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内置规则元数据：只描述匹配逻辑之外的可管理策略。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RuleMetadata {

    /** security_builtin_rules.id */
    private String id;
    /** 与子扫描器代码一致的规则 ID */
    private String ruleId;
    /** 管理分类：credential/file/unicode/network/tool/tracking/prompt */
    private String category;
    /** 规则名称 */
    private String name;
    /** 覆盖后的严重度 */
    private SecuritySeverity severity;
    /** 人读标题 */
    private String title;
    /** 规则说明 */
    private String description;
    /** 全局类别开关 key；null 表示只受 enabled 控制 */
    private String toggleKey;
    private boolean enabled;
    private String createdAt;
    private String updatedAt;
}
