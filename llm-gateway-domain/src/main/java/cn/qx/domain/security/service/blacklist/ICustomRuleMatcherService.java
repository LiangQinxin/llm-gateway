package cn.qx.domain.security.service.blacklist;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanText;

import java.util.List;

/**
 * 自定义黑白名单匹配领域服务端口。
 */
public interface ICustomRuleMatcherService {

    boolean isWhitelisted(String scannerName, String text);

    List<ScanFinding> customFindings(ScanText text);
}
