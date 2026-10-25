package cn.qx.domain.security.service.redact;

import cn.qx.api.ISecurityRuleCase;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityMode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 递归脱敏器端口测试：只验证转发前的 JSON 改写行为。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RecursiveRedactorTest {

    /** 被测的递归脱敏器：断言转发前对 JSON 与正文中密钥的改写行为。 */
    @Resource
    private IRecursiveRedactorService recursiveRedactor;

    /** 安全规则用例：动态创建自定义黑名单，验证脱敏器与扫描器共享规则缓存。 */
    @Resource
    private ISecurityRuleCase securityRuleCase;

    /**
     * 敏感键名的字符串值必须整值打码，非敏感字段保持不变。
     */
    @Test
    public void masks_secret_field_values_by_key() {
        String original = "{\"password\":\"root123\",\"model\":\"gpt-4o\"}";
        String redacted = recursiveRedactor.redact(
                original, ScanSettings.builder().mode(SecurityMode.REDACT).build());
        log.info("键名级脱敏: original={}, redacted={}", original, redacted);

        JSONObject body = JSON.parseObject(redacted);
        assertEquals("gpt-4o", body.getString("model"));
        assertFalse(body.getString("password").contains("root123"));
    }

    /**
     * 普通文本中的长密钥只替换命中的 token，周边正文保持不变。
     */
    @Test
    public void masks_secret_tokens_inside_text() {
        String secret = "sk-proj-abcdefghijklmnopqrstuvwx";
        String redacted = recursiveRedactor.redact(
                "{\"content\":\"use " + secret + " now\"}",
                ScanSettings.builder().mode(SecurityMode.REDACT).build());
        log.info("字符串级脱敏: {}", redacted);

        String content = JSON.parseObject(redacted).getString("content");
        assertTrue(content.startsWith("use sk-p****uvwx now"));
        assertFalse(content.contains(secret));
    }

    /**
     * PEM 私钥块整体替换，不留下 base64 正文。
     */
    @Test
    public void replaces_private_key_block() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEsecretbody\n"
                + "-----END RSA PRIVATE KEY-----";
        String redacted = recursiveRedactor.redact(
                "{\"content\":" + JSON.toJSONString(pem) + "}",
                ScanSettings.builder().mode(SecurityMode.REDACT).build());
        log.info("PEM 脱敏: {}", redacted);

        String content = JSON.parseObject(redacted).getString("content");
        assertEquals("[REDACTED PRIVATE KEY]", content);

        String encrypted = "-----BEGIN ENCRYPTED PRIVATE KEY-----\nMIIEsecretbody\n"
                + "-----END ENCRYPTED PRIVATE KEY-----";
        String encryptedRedacted = recursiveRedactor.redact(
                "{\"content\":" + JSON.toJSONString(encrypted) + "}",
                ScanSettings.builder().mode(SecurityMode.REDACT).build());
        assertEquals("[REDACTED PRIVATE KEY]",
                JSON.parseObject(encryptedRedacted).getString("content"));
    }

    /**
     * 数字、布尔与 null 保持原类型；普通短文本不被误伤。
     */
    @Test
    public void preserves_structural_values_and_plain_text() {
        String redacted = recursiveRedactor.redact(
                "{\"count\":12,\"enabled\":true,\"missing\":null,\"content\":\"hello\"}",
                ScanSettings.builder().mode(SecurityMode.REDACT).build());
        log.info("结构保真: {}", redacted);

        JSONObject body = JSON.parseObject(redacted);
        assertEquals(Integer.valueOf(12), body.getInteger("count"));
        assertEquals(Boolean.TRUE, body.getBoolean("enabled"));
        assertTrue(body.containsKey("missing"));
        assertEquals(null, body.get("missing"));
        assertEquals("hello", body.getString("content"));
    }

    /**
     * 非法 JSON 不抛异常，并尽可能对敏感赋值做正则兜底。
     */
    @Test
    public void invalid_json_falls_back_without_throwing() {
        String redacted = recursiveRedactor.redact(
                "password=superSecret123 trailing",
                ScanSettings.builder().mode(SecurityMode.REDACT).build());
        log.info("非法 JSON 兜底: {}", redacted);

        assertNotNull(redacted);
        assertFalse(redacted.contains("superSecret123"));
    }

    /**
     * 启用中的黑名单 pattern 大小写不敏感替换；短词整体替换，长词保留首尾掩码。
     */
    @Test
    public void redacts_custom_blacklist_patterns_from_shared_rule_cache() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String shortPattern = "short" + suffix.substring(0, 6);
        String longPattern = "longcustommarker" + suffix.substring(0, 4);
        String shortId = null;
        String longId = null;
        try {
            shortId = securityRuleCase.createCustom(customBlacklist(
                    "short-" + suffix, shortPattern)).getId();
            longId = securityRuleCase.createCustom(customBlacklist(
                    "long-" + suffix, longPattern)).getId();

            String shortText = "before " + shortPattern.toUpperCase() + " after";
            String longText = "before " + capitalize(longPattern) + " after";
            String original = JSON.toJSONString(java.util.Map.of(
                    "shortText", shortText, "longText", longText));
            String redacted = recursiveRedactor.redact(
                    original, ScanSettings.builder().mode(SecurityMode.REDACT).build());
            log.info("自定义 pattern 脱敏: original={}, redacted={}", original, redacted);

            JSONObject body = JSON.parseObject(redacted);
            assertEquals("before [REDACTED] after", body.getString("shortText"));
            assertEquals("before " + capitalize(longPattern).substring(0, 4)
                            + "****" + capitalize(longPattern)
                            .substring(capitalize(longPattern).length() - 4) + " after",
                    body.getString("longText"));
            assertFalse(redacted.contains(shortPattern));
            assertFalse(redacted.contains(longPattern));

            String invalidJson = "prefix " + longPattern.toUpperCase() + " suffix";
            String fallback = recursiveRedactor.redact(
                    invalidJson, ScanSettings.builder().mode(SecurityMode.REDACT).build());
            log.info("自定义 pattern 非法 JSON 脱敏: {}", fallback);
            assertFalse(fallback.contains(longPattern.toUpperCase()));
            assertTrue(fallback.contains("****"));
        } finally {
            if (shortId != null) {
                securityRuleCase.deleteCustom(shortId);
            }
            if (longId != null) {
                securityRuleCase.deleteCustom(longId);
            }
        }
    }

    private static CustomRuleRequest customBlacklist(String name, String pattern) {
        CustomRuleRequest request = new CustomRuleRequest();
        request.setRuleType("blacklist");
        request.setCategory("keyword");
        request.setName(name);
        request.setPattern(pattern);
        request.setSeverity("high");
        request.setAction("redact");
        request.setEnabled(true);
        request.setDescription("redactor custom pattern test");
        return request;
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
