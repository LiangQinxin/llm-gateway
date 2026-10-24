package cn.qx.api;

/**
 * 数据面请求安全审计用例接口。
 */
public interface ISecurityAuditCase {

    /**
     * 扫描请求并在脱敏时返回改写后的 body；阻断时抛 403。
     */
    String auditRequest(String body, String model, String inboundPath);
}
