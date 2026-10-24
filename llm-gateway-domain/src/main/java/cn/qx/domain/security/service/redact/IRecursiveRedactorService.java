package cn.qx.domain.security.service.redact;

import cn.qx.domain.security.model.valobj.ScanSettings;

/**
 * 递归脱敏领域服务端口。
 */
public interface IRecursiveRedactorService {

    /** 转发前对请求体做结构化 JSON 递归脱敏（敏感键名整值打码、文本内密钥前缀打码），返回脱敏后字符串。 */
    String redact(String body, ScanSettings settings);
}
