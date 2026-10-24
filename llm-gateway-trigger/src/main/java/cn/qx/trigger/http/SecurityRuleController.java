package cn.qx.trigger.http;

import cn.qx.api.ISecurityRuleCase;
import cn.qx.api.dto.common.ToggleRequest;
import cn.qx.api.dto.security.BuiltinRuleUpdateRequest;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.api.dto.security.BuiltinRuleResult;
import cn.qx.api.dto.security.CustomRuleResult;
import cn.qx.api.dto.security.CustomRuleUpdateRequest;
import cn.qx.api.response.Response;
import cn.qx.api.response.Responses;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 安全规则管理 Controller（管理面 /admin/api/security/rules/**）。
 */
@RestController
@RequestMapping("/admin/api/security/rules")
public class SecurityRuleController {

    /** 安全规则用例：内置规则与自定义规则的管理编排。 */
    @Resource
    private ISecurityRuleCase securityRuleCase;

    @GetMapping("/builtin")
    public Response<List<BuiltinRuleResult>> listBuiltin() {
        return Responses.success(securityRuleCase.listBuiltin());
    }

    @PutMapping("/builtin/{ruleId}")
    public Response<BuiltinRuleResult> updateBuiltin(
            @PathVariable("ruleId") String ruleId,
            @RequestBody BuiltinRuleUpdateRequest request) {
        return Responses.success(securityRuleCase.updateBuiltin(ruleId, request));
    }

    @PostMapping("/builtin/reset")
    public Response<Void> resetBuiltin() {
        securityRuleCase.resetBuiltin();
        return Responses.success(null);
    }

    @GetMapping("/custom")
    public Response<List<CustomRuleResult>> listCustom(
            @RequestParam(name = "ruleType", required = false) String ruleType) {
        return Responses.success(securityRuleCase.listCustom(ruleType));
    }

    @PostMapping("/custom")
    public Response<CustomRuleResult> createCustom(@RequestBody CustomRuleRequest request) {
        return Responses.success(securityRuleCase.createCustom(request));
    }

    @PutMapping("/custom/{id}")
    public Response<CustomRuleResult> updateCustom(
            @PathVariable("id") String id,
            @RequestBody CustomRuleUpdateRequest request) {
        return Responses.success(securityRuleCase.updateCustom(id, request));
    }

    @PatchMapping("/custom/{id}/enabled")
    public Response<CustomRuleResult> toggleCustom(
            @PathVariable("id") String id,
            @RequestBody ToggleRequest request) {
        return Responses.success(securityRuleCase.toggleCustom(id, request.isEnabled()));
    }

    @DeleteMapping("/custom/{id}")
    public Response<Void> deleteCustom(@PathVariable("id") String id) {
        securityRuleCase.deleteCustom(id);
        return Responses.success(null);
    }

}
