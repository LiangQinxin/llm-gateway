package cn.qx.domain.security;

import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityMode;

/**
 * 安全扫描测试设置构造器。
 */
public final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    public static ScanSettings credentialOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).path(false).build();
    }

    public static ScanSettings pathOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).credential(false).build();
    }

    public static ScanSettings unicodeOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).credential(false).path(false).unicode(true).build();
    }

    public static ScanSettings networkOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).credential(false).path(false).network(true).build();
    }

    public static ScanSettings toolOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).credential(false).path(false).toolRisk(true).build();
    }

    public static ScanSettings trackingOnly(SecurityMode mode) {
        return ScanSettings.builder().mode(mode).credential(false).path(false).tracking(true).build();
    }
}
