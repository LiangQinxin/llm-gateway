package cn.qx.config;

import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityMode;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * security.audit.* 配置映射。
 */
@Data
@ConfigurationProperties(prefix = "security.audit")
public class SecurityAuditProperties {

    private boolean enabled = true;
    private String mode = "warn";
    private boolean blockCritical = false;
    private boolean scanRequest = true;
    private boolean scanResponse = false;
    private int maxScanBytes = 0;
    private Scanners scanners = new Scanners();

    public ScanSettings toSettings() {
        return ScanSettings.builder()
                .enabled(enabled)
                .mode(SecurityMode.parse(mode))
                .blockCritical(blockCritical)
                .scanRequest(scanRequest)
                .scanResponse(scanResponse)
                .maxScanBytes(maxScanBytes)
                .credential(scanners.credential)
                .path(scanners.path)
                .unicode(scanners.unicode)
                .network(scanners.network)
                .toolRisk(scanners.toolRisk)
                .tracking(scanners.tracking)
                .build();
    }

    @Data
    public static class Scanners {
        private boolean credential = true;
        private boolean path = true;
        private boolean unicode = false;
        private boolean network = false;
        private boolean toolRisk = false;
        private boolean tracking = false;
    }
}
