package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * security_builtin_rules 表持久化对象，字段与库表列一一对应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityBuiltinRulePO {

    /** id 列 */
    private String id;
    /** rule_id 列 */
    private String ruleId;
    /** category 列：credential/file/unicode/network/tool/tracking/prompt */
    private String category;
    /** name 列 */
    private String name;
    /** severity 列 */
    private String severity;
    /** pattern 列：遗留列，不落值 */
    private String pattern;
    /** action 列：遗留列，不落值 */
    private String action;
    /** title 列 */
    private String title;
    /** description 列 */
    private String description;
    /** toggle_key 列 */
    private String toggleKey;
    /** enabled 列 */
    private Integer enabled;
    /** created_at 列 */
    private String createdAt;
    /** updated_at 列 */
    private String updatedAt;
}
