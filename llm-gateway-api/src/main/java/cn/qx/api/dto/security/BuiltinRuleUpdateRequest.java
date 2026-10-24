package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 内置规则可变字段；ruleId/category/toggleKey 不通过请求修改。
 */
@Data
public class BuiltinRuleUpdateRequest {

    private String severity;
    private Boolean enabled;
    private String title;
    private String description;
}
