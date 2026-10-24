package cn.qx.api;

import cn.qx.api.dto.security.BuiltinRuleResult;
import cn.qx.api.dto.security.BuiltinRuleUpdateRequest;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.api.dto.security.CustomRuleResult;
import cn.qx.api.dto.security.CustomRuleUpdateRequest;

import java.util.List;

/**
 * 安全规则管理用例接口。
 */
public interface ISecurityRuleCase {

    List<BuiltinRuleResult> listBuiltin();

    BuiltinRuleResult getBuiltin(String ruleId);

    BuiltinRuleResult updateBuiltin(String ruleId, BuiltinRuleUpdateRequest request);

    void resetBuiltin();

    List<CustomRuleResult> listCustom(String ruleType);

    CustomRuleResult createCustom(CustomRuleRequest request);

    CustomRuleResult updateCustom(String id, CustomRuleUpdateRequest request);

    CustomRuleResult toggleCustom(String id, boolean enabled);

    void deleteCustom(String id);
}
