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

import static org.junit.Assert.assertTrue;

/**
 * PathScanner 端口测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class PathScannerTest {

    @Resource
    private ISecurityScanner securityScanner;

    /**
     * Unix/Windows 敏感路径、项目文件与路径穿越分别命中预期规则。
     */
    @Test
    public void sensitive_paths_and_traversal_are_detected() {
        String body = """
                cat ~/.ssh/id_rsa /etc/passwd C:\\Users\\alice\\.ssh\\id_rsa
                database.yml ../../etc/passwd %2e%2e%2f%2e%2e%2fetc %2e%2e%5c%2e%2e%5cwindows
                """;
        AuditResult result = securityScanner.scan(ScanRequest.of(
                body, SecurityTestSupport.pathOnly(SecurityMode.WARN)));
        log.info("路径扫描结果: {}", JSON.toJSONString(result));

        Set<String> rules = result.getFindings().stream()
                .map(ScanFinding::getRuleId).collect(Collectors.toSet());
        assertTrue(rules.contains("path_sensitive_unix"));
        assertTrue(rules.contains("path_sensitive_windows"));
        assertTrue(rules.contains("path_sensitive_project"));
        assertTrue(rules.contains("path_traversal"));
        assertTrue(result.getFindings().stream()
                .allMatch(f -> f.getCategories().contains(SecurityCategory.SENSITIVE_FILE)));
        assertTrue(result.getFindings().stream()
                .anyMatch(f -> f.getSeverity() == SecuritySeverity.HIGH));
    }

    /**
     * 普通系统路径和项目文件名不产生误报。
     */
    @Test
    public void ordinary_paths_do_not_trigger() {
        AuditResult result = securityScanner.scan(ScanRequest.of(
                "open /usr/local/bin/java and src/Main.java",
                SecurityTestSupport.pathOnly(SecurityMode.WARN)));
        log.info("普通路径结果: {}", JSON.toJSONString(result));
        assertTrue("普通路径不应命中", result.getFindings().isEmpty());
    }
}
