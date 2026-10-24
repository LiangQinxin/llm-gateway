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

    private String id;
    /** blacklist | whitelist */
    private String ruleType;
    /** domain | tool | path | keyword */
    private String category;
    private String name;
    private String pattern;
    private SecuritySeverity severity;
    /** 管理提示动作，实际处置仍由统一评分矩阵决定 */
    private String action;
    private boolean enabled;
    private String description;
    private String createdAt;
    private String updatedAt;
}
