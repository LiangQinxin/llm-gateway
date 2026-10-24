package cn.qx.api.dto.security;

import lombok.Data;

/**
 * 自定义黑名单/白名单请求体。
 */
@Data
public class CustomRuleRequest {

    /** 规则类型（blacklist|whitelist） */
    private String ruleType;
    /** 规则类别（domain|tool|path|keyword） */
    private String category;
    /** 规则名称（空=取 pattern） */
    private String name;
    /** 匹配模式（子串匹配，禁用正则防 ReDoS） */
    private String pattern;
    /** 风险严重度（自定义规则必填） */
    private String severity;
    /** 命中动作（warn|redact|block） */
    private String action;
    /** 是否启用（null=默认启用） */
    private Boolean enabled;
    /** 规则描述（空=空串） */
    private String description;
}
