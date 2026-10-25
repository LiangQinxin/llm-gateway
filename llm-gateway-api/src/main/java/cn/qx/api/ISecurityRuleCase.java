package cn.qx.api;

import cn.qx.api.dto.security.BuiltinRuleResult;
import cn.qx.api.dto.security.BuiltinRuleUpdateRequest;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.api.dto.security.CustomRuleResult;
import cn.qx.api.dto.security.CustomRuleUpdateRequest;
import cn.qx.api.dto.security.SecurityFindingResult;

import java.util.List;

/**
 * 安全规则管理用例接口。
 */
public interface ISecurityRuleCase {

    /** 列出全部内置规则（来自进程内缓存）。 */
    List<BuiltinRuleResult> listBuiltin();

    /** 查询内置规则详情，不存在抛 NOT_FOUND。 */
    BuiltinRuleResult getBuiltin(String ruleId);

    /** 更新内置规则可变字段（严重度/启停/标题/描述），写后刷新规则缓存。 */
    BuiltinRuleResult updateBuiltin(String ruleId, BuiltinRuleUpdateRequest request);

    /** 清空内置规则表并按出厂目录重新播种，恢复默认配置。 */
    void resetBuiltin();

    /** 列出自定义黑名单/白名单规则，可按 ruleType 过滤。 */
    List<CustomRuleResult> listCustom(String ruleType);

    /** 创建自定义规则，校验类型/类别/模式后落库并刷新规则缓存。 */
    CustomRuleResult createCustom(CustomRuleRequest request);

    /** 更新自定义规则可变字段（模式/严重度/动作/描述）。 */
    CustomRuleResult updateCustom(String id, CustomRuleUpdateRequest request);

    /** 启用或停用自定义规则。 */
    CustomRuleResult toggleCustom(String id, boolean enabled);

    /** 删除自定义规则。 */
    void deleteCustom(String id);

    /** 按创建时间倒序取最近 N 条安全发现记录（管理面审计列表）。 */
    List<SecurityFindingResult> listFindings(int limit);
}
