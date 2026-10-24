package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 自定义规则 PUT 可变字段；类型/类别/名称与启停不走此接口。
 */
@Data
public class CustomRuleUpdateRequest {

    private String pattern;
    private String severity;
    private String action;
    private String description;
}
