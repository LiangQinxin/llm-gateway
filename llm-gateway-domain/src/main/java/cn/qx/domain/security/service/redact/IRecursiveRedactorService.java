package cn.qx.domain.security.service.redact;

import cn.qx.domain.security.model.valobj.ScanSettings;

/**
 * 递归脱敏领域服务端口。
 */
public interface IRecursiveRedactorService {

    String redact(String body, ScanSettings settings);
}
