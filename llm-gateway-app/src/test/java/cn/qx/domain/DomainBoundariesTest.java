package cn.qx.domain;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 十域边界与同构目录结构校验。
 */
public class DomainBoundariesTest {

    private static final List<String> DOMAINS = List.of(
            "proxy", "channel", "identity", "security", "audit",
            "account", "settings", "knowledge", "wiki", "mcp");

    @Test
    public void ten_domains_keep_isomorphic_layer_structure() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (root.getFileName().toString().equals("llm-gateway-app")) {
            root = root.getParent();
        }
        for (String domain : DOMAINS) {
            Path base = root.resolve("llm-gateway-domain/src/main/java/cn/qx/domain").resolve(domain);
            assertTrue("domain 缺少 model: " + domain, Files.isDirectory(base.resolve("model")));
            assertTrue("domain 缺少 service: " + domain, Files.isDirectory(base.resolve("service")));
            assertTrue("domain 缺少 adapter: " + domain, Files.isDirectory(base.resolve("adapter")));
            assertTrue("case 缺少域包: " + domain,
                    Files.isDirectory(root.resolve("llm-gateway-case/src/main/java/cn/qx/cases").resolve(domain)));
        }
        assertFalse("quota 不应独立成域",
                Files.exists(root.resolve("llm-gateway-domain/src/main/java/cn/qx/domain/quota")));
    }
}
