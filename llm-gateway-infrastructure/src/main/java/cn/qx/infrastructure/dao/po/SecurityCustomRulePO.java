package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * security_custom_rules 表持久化对象，字段与库表列一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityCustomRulePO {

    /** id 列 */
    private String id;
    /** rule_type 列：blacklist/whitelist */
    private String ruleType;
    /** category 列：domain/tool/path/keyword */
    private String category;
    /** name 列 */
    private String name;
    /** pattern 列：子串匹配内容 */
    private String pattern;
    /** severity 列 */
    private String severity;
    /** action 列：规则级动作(warn/redact/block)，NULL=跟随全局策略 */
    private String action;
    /** enabled 列 */
    private Integer enabled;
    /** description 列 */
    private String description;
    /** created_at 列 */
    private String createdAt;
    /** updated_at 列 */
    private String updatedAt;
}
