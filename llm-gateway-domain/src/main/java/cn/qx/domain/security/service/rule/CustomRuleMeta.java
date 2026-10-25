package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.SecuritySeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自定义黑名单/白名单规则；pattern 使用大小写不敏感的子串匹配。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CustomRuleMeta {

    /** 主键 ID（UUID）。 */
    private String id;
    /** blacklist | whitelist */
    private String ruleType;
    /** domain | tool | path | keyword */
    private String category;
    /** 规则名称（人读展示，命中后作为发现标题）。 */
    private String name;
    /** 匹配模式：大小写不敏感子串（禁用正则，防 ReDoS）。 */
    private String pattern;
    /** 命中后并入统一评分矩阵的严重度。 */
    private SecuritySeverity severity;
    /** 规则级命中动作（warn/redact/block）；null 表示跟随全局 mode */
    private String action;
    /** 是否启用。 */
    private boolean enabled;
    /** 规则说明（黑名单命中后作为发现描述）。 */
    private String description;
    /** 创建时间（ISO-8601 带偏移）。 */
    private String createdAt;
    /** 更新时间（ISO-8601 带偏移）。 */
    private String updatedAt;
}
