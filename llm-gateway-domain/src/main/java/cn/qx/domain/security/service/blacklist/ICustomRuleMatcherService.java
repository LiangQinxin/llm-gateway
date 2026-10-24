package cn.qx.domain.security.service.blacklist;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanText;

import java.util.List;

/**
 * 自定义黑白名单匹配领域服务端口。
 */
public interface ICustomRuleMatcherService {

    /** 判定文本在指定子扫描器类别下是否被白名单豁免（命中即跳过该扫描器）。 */
    boolean isWhitelisted(String scannerName, String text);

    /** 对文本跑黑名单子串匹配，命中即产生 category=custom 的发现。 */
    List<ScanFinding> customFindings(ScanText text);
}
