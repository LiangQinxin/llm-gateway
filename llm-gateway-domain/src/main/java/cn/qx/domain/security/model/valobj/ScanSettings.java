package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全扫描开关与全局策略。
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

    @Builder.Default
    private boolean blockCritical = false;

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
