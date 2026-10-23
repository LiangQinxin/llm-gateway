package cn.qx.config;

import cn.qx.domain.security.model.valobj.ScanSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全审计配置装配。
 */
@Configuration
@EnableConfigurationProperties(SecurityAuditProperties.class)
public class SecurityAuditConfiguration {

    @Bean
    public ScanSettings securityScanSettings(SecurityAuditProperties properties) {
        return properties.toSettings();
    }
}
