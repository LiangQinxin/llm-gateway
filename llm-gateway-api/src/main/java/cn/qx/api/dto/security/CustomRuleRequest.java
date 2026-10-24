package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 自定义黑名单/白名单请求体。
 */
@Data
public class CustomRuleRequest {

    private String ruleType;
    private String category;
    private String name;
    private String pattern;
    private String severity;
    private String action;
    private Boolean enabled;
    private String description;
}
