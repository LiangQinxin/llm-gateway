package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全扫描开关与全局策略；对齐安全审计引擎 SecuritySettings：
 * enabled / mode / scan_request / scan_response / block_on_critical / max_scan_bytes。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScanSettings {

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private SecurityMode mode = SecurityMode.WARN;

    /** 请求侧扫描开关。 */
    @Builder.Default
    private boolean scanRequest = true;

    /** 响应侧扫描开关；默认关闭，开启后响应体同样过扫描引擎。 */
    @Builder.Default
    private boolean scanResponse = false;

    @Builder.Default
    private boolean blockCritical = false;

    /** 单字段最大扫描字节数（性能保护）；0 表示不限制。 */
    @Builder.Default
    private int maxScanBytes = 0;

    @Builder.Default
    private boolean credential = true;

    @Builder.Default
    private boolean path = true;

    @Builder.Default
    private boolean unicode = false;

    @Builder.Default
    private boolean network = false;

    @Builder.Default
    private boolean toolRisk = false;

    @Builder.Default
    private boolean tracking = false;

    public static ScanSettings defaults() {
        return ScanSettings.builder().build();
    }

    public static ScanSettings allScanners(SecurityMode mode) {
        return ScanSettings.builder()
                .mode(mode)
                .unicode(true)
                .network(true)
                .toolRisk(true)
                .tracking(true)
                .build();
    }
}
