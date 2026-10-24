package cn.qx.api;

import cn.qx.api.dto.security.SecurityAuditOutcome;

/**
 * 数据面请求安全审计用例接口。
 */
public interface ISecurityAuditCase {

    /**
     * 扫描请求并返回转发体、脱敏落库体、审计结论与阻断标记。
     * <p>纯决策：不写日志、不抛业务异常，由 ProxyCase 统一留痕与映射状态码。
     */
    SecurityAuditOutcome auditRequest(String body, String model, String inboundPath);
}
