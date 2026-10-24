package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanRequest;

/**
 * 安全扫描引擎端口。
 */
public interface ISecurityScanner {

    /** 按扫描请求执行安全审计：分派子扫描器、汇总发现、评分并产出动作。 */
    AuditResult scan(ScanRequest request);

    /** 以默认扫描配置扫描请求体原文。 */
    AuditResult scan(String body);
}
