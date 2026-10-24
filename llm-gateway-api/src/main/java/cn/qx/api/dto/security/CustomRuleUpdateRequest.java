package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 自定义规则 PUT 可变字段；类型/类别/名称与启停不走此接口。
 */
@Data
public class CustomRuleUpdateRequest {

    /** 新匹配模式（null=不改） */
    private String pattern;
    /** 新严重度（null=不改） */
    private String severity;
    /** 新命中动作（null=不改） */
    private String action;
    /** 新描述（null=不改） */
    private String description;
}
