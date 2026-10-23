package cn.qx.domain.security.service;

import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanRequest;

/**
 * 安全扫描引擎端口。
 */
public interface ISecurityScanner {

    AuditResult scan(ScanRequest request);

    AuditResult scan(String body);
}
