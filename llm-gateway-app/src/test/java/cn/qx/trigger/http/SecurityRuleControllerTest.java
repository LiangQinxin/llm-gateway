package cn.qx.trigger.http;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.ISecurityScanner;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 安全规则管理面 Controller 真实 HTTP 测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityRuleControllerTest {

    /** 随机端口（测试用）：RANDOM_PORT 模式下注入的本机 HTTP 服务端口。 */
    @LocalServerPort
    private int port;

    /** 安全扫描引擎：在管理面改配置后立即扫描，断言规则写操作已生效。 */
    @Resource
    private ISecurityScanner securityScanner;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 8 个管理接口冒烟：内置查询/修改/重置，自定义查询/新增/修改/启停/删除。
     */
    @Test
    public void security_rule_management_endpoints_work_end_to_end() throws Exception {
        JSONObject builtinBefore = send("GET", "/admin/api/security/rules/builtin", null);
        JSONArray builtinRules = builtinBefore.getJSONArray("data");
        assertTrue("内置规则应已播种", builtinRules.size() >= 65);
        JSONObject openaiRule = builtinRules.stream()
                .map(JSONObject.class::cast)
                .filter(rule -> "credential_apikey_openai".equals(rule.getString("ruleId")))
                .findFirst().orElseThrow();
        String originalSeverity = openaiRule.getString("severity");

        JSONObject changed = new JSONObject();
        changed.put("severity", "low");
        changed.put("enabled", true);
        JSONObject updatedBuiltin = send("PUT",
                "/admin/api/security/rules/builtin/credential_apikey_openai",
                changed.toJSONString());
        assertEquals("LOW", updatedBuiltin.getJSONObject("data").getString("severity"));
        assertTrue("管理写操作后缓存应立即生效",
                securityScanner.scan(ScanRequest.of(
                                "sk-proj-abcdefghijklmnopqrstuvwx",
                                ScanSettings.builder()
                                        .mode(SecurityMode.WARN)
                                        .path(false)
                                        .build()))
                        .getFindings().stream()
                        .anyMatch(finding -> "credential_apikey_openai".equals(finding.getRuleId())
                                && finding.getSeverity() == SecuritySeverity.LOW));

        JSONObject restore = new JSONObject();
        restore.put("severity", originalSeverity);
        restore.put("enabled", true);
        send("PUT", "/admin/api/security/rules/builtin/credential_apikey_openai",
                restore.toJSONString());

        JSONObject reset = send("POST", "/admin/api/security/rules/builtin/reset", "{}");
        log.info("内置规则重置响应: {}", reset);
        assertEquals("0000", reset.getString("code"));

        String suffix = UUID.randomUUID().toString().replace("-", "");
        JSONObject create = new JSONObject();
        create.put("ruleType", "blacklist");
        create.put("category", "keyword");
        create.put("name", "controller-test-" + suffix);
        create.put("pattern", "marker-" + suffix);
        create.put("severity", "high");
        create.put("action", "block");
        create.put("enabled", true);
        create.put("description", "controller integration test");
        JSONObject created = send("POST", "/admin/api/security/rules/custom", create.toJSONString());
        String id = created.getJSONObject("data").getString("id");
        assertFalse(id.isBlank());

        try {
            JSONObject list = send("GET",
                    "/admin/api/security/rules/custom?ruleType=blacklist", null);
            assertTrue(list.getJSONArray("data").stream()
                    .map(JSONObject.class::cast)
                    .anyMatch(rule -> id.equals(rule.getString("id"))));

            JSONObject update = new JSONObject();
            update.put("severity", "critical");
            JSONObject updated = send("PUT",
                    "/admin/api/security/rules/custom/" + id, update.toJSONString());
            assertEquals("CRITICAL", updated.getJSONObject("data").getString("severity"));
            assertEquals(created.getJSONObject("data").getString("ruleType"),
                    updated.getJSONObject("data").getString("ruleType"));
            assertEquals(created.getJSONObject("data").getString("category"),
                    updated.getJSONObject("data").getString("category"));
            assertEquals(created.getJSONObject("data").getString("name"),
                    updated.getJSONObject("data").getString("name"));

            JSONObject toggle = send("PATCH",
                    "/admin/api/security/rules/custom/" + id + "/enabled",
                    "{\"enabled\":false}");
            assertFalse(toggle.getJSONObject("data").getBooleanValue("enabled"));
        } finally {
            JSONObject deleted = send("DELETE",
                    "/admin/api/security/rules/custom/" + id, null);
            assertEquals("0000", deleted.getString("code"));
        }
    }

    private JSONObject send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method,
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("安全规则 HTTP: {} {} -> status={}, body={}",
                method, path, response.statusCode(), response.body());
        assertEquals(200, response.statusCode());
        JSONObject json = JSON.parseObject(response.body());
        assertEquals("0000", json.getString("code"));
        return json;
    }
}
