package cn.qx.api;

import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.model.valobj.SecurityMode;
import cn.qx.domain.security.service.ISecurityScanner;
import cn.qx.domain.security.service.redact.IRecursiveRedactorService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全规则动作管理接口契约测试。
 * <p>真实 Spring 容器 + 本地 MySQL：管理面写 action 后，扫描与脱敏立即读取同一份规则缓存。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class SecurityRuleActionApiTest {

    /** MockMvc 模拟管理面 HTTP 请求。 */
    @Autowired
    private MockMvc mockMvc;

    /** 安全扫描引擎：断言规则动作已即时生效。 */
    @Resource
    private ISecurityScanner securityScanner;

    /** 递归脱敏器：断言自定义 pattern 与扫描器共享缓存。 */
    @Resource
    private IRecursiveRedactorService recursiveRedactor;

    /**
     * 内置 action 更新/清空与自定义 action 创建/删除全链路。
     */
    @Test
    public void rule_action_round_trip_takes_effect_immediately() throws Exception {
        String originalAction = builtinAction("credential_apikey_openai");
        String secret = "sk-proj-abcdefghijklmnopqrstuvwx";
        try {
            updateBuiltinAction("credential_apikey_openai", "block")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.action").value("block"));
            AuditResult blocked = scan(secret, SecurityMode.WARN);
            log.info("管理面内置 block 生效: {}", JSON.toJSONString(blocked));
            assertEquals(SecurityAction.BLOCK, blocked.getFinalAction());

            updateBuiltinAction("credential_apikey_openai", "")
                    .andExpect(status().isOk());
            AuditResult cleared = scan(secret, SecurityMode.WARN);
            log.info("管理面内置 action 清空: {}", JSON.toJSONString(cleared));
            assertEquals(SecurityAction.ALLOW, cleared.getFinalAction());
        } finally {
            updateBuiltinAction("credential_apikey_openai",
                    originalAction == null ? "" : originalAction);
        }

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String longPattern = "apicustommarker" + suffix.substring(0, 4);
        String marker = "marker-" + suffix;
        JSONObject create = new JSONObject();
        create.put("ruleType", "blacklist");
        create.put("category", "keyword");
        create.put("name", "api-action-" + suffix);
        create.put("pattern", longPattern);
        create.put("severity", "low");
        create.put("action", "redact");
        create.put("enabled", true);
        create.put("description", "controller action test");
        MvcResult created = mockMvc.perform(post("/admin/api/security/rules/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("redact"))
                .andReturn();
        String id = JSON.parseObject(created.getResponse().getContentAsString())
                .getJSONObject("data").getString("id");
        log.info("管理面自定义规则: {}", created.getResponse().getContentAsString());
        try {
            AuditResult redacted = scan(marker + " " + longPattern, SecurityMode.AUDIT);
            log.info("管理面自定义 redact 生效: {}", JSON.toJSONString(redacted));
            assertTrue(redacted.getFindings().stream()
                    .anyMatch(finding -> "custom.blacklist.keyword".equals(finding.getRuleId())
                            && finding.getAction() == FindingAction.REDACT));
            assertEquals(SecurityAction.SANITIZE, redacted.getFinalAction());

            String body = JSON.toJSONString(JSONObject.of(
                    "content", marker + " " + longPattern.toUpperCase()));
            String sanitized = recursiveRedactor.redact(
                    body, ScanSettings.builder().mode(SecurityMode.REDACT).build());
            log.info("管理面自定义 pattern 脱敏: {}", sanitized);
            assertFalse(sanitized.contains(longPattern.toUpperCase()));
        } finally {
            mockMvc.perform(delete("/admin/api/security/rules/custom/{id}", id))
                    .andExpect(status().isOk());
        }
    }

    private org.springframework.test.web.servlet.ResultActions updateBuiltinAction(
            String ruleId, String action) throws Exception {
        return mockMvc.perform(put("/admin/api/security/rules/builtin/{ruleId}", ruleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(JSONObject.of("action", action))));
    }

    private String builtinAction(String ruleId) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/api/security/rules/builtin"))
                .andExpect(status().isOk())
                .andReturn();
        JSONArray rules = JSON.parseObject(result.getResponse().getContentAsString())
                .getJSONArray("data");
        JSONObject rule = rules.stream()
                .map(JSONObject.class::cast)
                .filter(item -> ruleId.equals(item.getString("ruleId")))
                .findFirst().orElseThrow();
        log.info("内置规则当前 action: ruleId={}, action={}",
                ruleId, rule.getString("action"));
        return rule.getString("action");
    }

    private AuditResult scan(String body, SecurityMode mode) {
        return securityScanner.scan(ScanRequest.of(body, ScanSettings.builder()
                .mode(mode)
                .path(false)
                .build()));
    }
}
