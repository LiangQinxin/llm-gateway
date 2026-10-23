package cn.qx.domain.security.adapter.port;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.ScanText;

import java.util.List;

/**
 * 子扫描器端口：一类风险的最小检查单元。
 */
public interface ISubScanner {

    /** 扫描器标识，与规则前缀一致。 */
    String scannerName();

    /** 当前设置下是否启用。 */
    boolean enabled(ScanSettings settings);

    /** 扫描一段文本。 */
    List<ScanFinding> scan(ScanText text);
}
