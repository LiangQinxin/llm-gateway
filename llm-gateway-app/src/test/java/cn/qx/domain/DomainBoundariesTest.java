package cn.qx.domain;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 十域边界与同构目录结构校验。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class DomainBoundariesTest {

    /** 十域清单：与 AGENTS.md 约定的域集合保持一致 */
    private static final List<String> DOMAINS = List.of(
            "proxy", "channel", "identity", "security", "audit",
            "account", "settings", "knowledge", "wiki", "mcp");

    /**
     * 十域同构结构校验：每个域必须含 model/service/adapter 三层目录，
     * case 层存在对应域包，且 quota 不得独立成域。
     */
    @Test
    public void ten_domains_keep_isomorphic_layer_structure() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (root.getFileName().toString().equals("llm-gateway-app")) {
            root = root.getParent();
        }
        log.info("十域结构校验根目录: {}", root);
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
