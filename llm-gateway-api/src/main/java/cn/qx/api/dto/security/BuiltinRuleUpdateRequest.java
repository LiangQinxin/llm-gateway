package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 内置规则可变字段；ruleId/category/toggleKey 不通过请求修改。
 */
@Data
public class BuiltinRuleUpdateRequest {

    /** 新严重度（空=保持原值） */
    private String severity;
    /** 新规则动作（null=保持原值，空串=清空并跟随全局 mode） */
    private String action;
    /** 新启停（null=保持原值） */
    private Boolean enabled;
    /** 新标题（空=保持原值） */
    private String title;
    /** 新描述（空=保持原值） */
    private String description;
}
