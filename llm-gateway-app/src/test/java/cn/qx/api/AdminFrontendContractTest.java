package cn.qx.api;

import cn.qx.cases.audit.RequestLogCase;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.types.common.TimeUtils;
import com.alibaba.fastjson2.JSON;
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

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理面-前端契约测试：覆盖仪表盘聚合、安全发现列表、日志 keyword 搜索、
 * 渠道 models 数组视图与日志布尔视图，验证前端对接的接口契约。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AdminFrontendContractTest {

    /** MockMvc 模拟 MVC 请求客户端 */
    @Autowired
    private MockMvc mockMvc;

    /** 请求日志用例 */
    @Resource
    private RequestLogCase requestLogCase;

    /** 安全发现仓储端口 */
    @Resource
    private ISecurityFindingRepository securityFindingRepository;

    /**
     * 仪表盘聚合接口：结构契约 + 相对关系（请求量/错误数/token 随本用例数据单调递增）。
     * <p>stats 为全表聚合，库中可能存在历史数据，故不做精确值断言。
     */
    @Test
    public void dashboard_aggregates_request_metrics() throws Exception {
        long before = requestLogCase.stats().getTotalRequests();
        requestLogCase.record(RequestLog.builder()
                .model("dash-model")
                .statusCode(200)
                .totalTokens(100L)
                .durationMs(50L)
                .build());
        requestLogCase.record(RequestLog.builder()
                .model("dash-model")
                .statusCode(500)
                .totalTokens(200L)
                .durationMs(150L)
                .build());

        MvcResult result = mockMvc.perform(get("/admin/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.totalRequests").isNumber())
                .andExpect(jsonPath("$.data.totalTokens").isNumber())
                .andExpect(jsonPath("$.data.totalErrors").isNumber())
                .andExpect(jsonPath("$.data.healthScore").isNumber())
                .andExpect(jsonPath("$.data.healthBadge").isNotEmpty())
                .andReturn();
        JSONObject dash = JSON.parseObject(result.getResponse().getContentAsString()).getJSONObject("data");
        assertTrue("请求量应随插入数据增加", dash.getLongValue("totalRequests") >= before + 2);
        assertTrue("错误数应至少包含新增的 500 请求", dash.getLongValue("totalErrors") >= 1);
        log.info("dashboard 契约响应: {}", result.getResponse().getContentAsString());
    }

    /**
     * 安全发现列表接口：写入 finding 后按创建时间倒序返回，severity 转大写。
     */
    @Test
    public void security_findings_list_returns_records() throws Exception {
        String now = TimeUtils.now();
        securityFindingRepository.insertAll("finding-log-1", ScanPhase.REQUEST, List.of(
                ScanFinding.builder()
                        .ruleId("credential.api_key")
                        .severity(SecuritySeverity.HIGH)
                        .title("检测到 API Key")
                        .description("请求体中包含密钥")
                        .evidence("sk-ant-a****x123")
                        .evidenceMasked("sk-ant-a****x123")
                        .location("@raw[0..8]")
                        .build()), now);

        MvcResult result = mockMvc.perform(get("/admin/api/security/rules/findings")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.data[0].title").value("检测到 API Key"))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andReturn();
        log.info("findings 契约响应: {}", result.getResponse().getContentAsString());
    }

    /**
     * 日志分页 keyword 模糊搜索：命中 model / trace_id / api_key_name 等字段。
     */
    @Test
    public void request_log_keyword_fuzzy_search() throws Exception {
        requestLogCase.record(RequestLog.builder()
                .model("kw-match-model")
                .apiKeyName("合同审核密钥")
                .statusCode(200)
                .build());
        requestLogCase.record(RequestLog.builder()
                .model("other-model")
                .statusCode(200)
                .build());

        MvcResult result = mockMvc.perform(get("/admin/api/logs")
                        .param("keyword", "合同审核")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].apiKeyName").value("合同审核密钥"))
                .andReturn();
        log.info("keyword 契约响应: {}", result.getResponse().getContentAsString());
    }

    /**
     * 渠道管理面视图：models 从 JSON 字符串转换为数组。
     */
    @Test
    public void channel_view_models_is_array() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/api/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"契约测试渠道",
                                  "type":"deepseek",
                                  "baseUrl":"http://127.0.0.1:8777/v1",
                                  "apiKey":"sk-test",
                                  "models":"[\\"deepseek-chat\\",\\"deepseek-flash\\"]",
                                  "priority":10,
                                  "weight":1,
                                  "config":"{}",
                                  "modelMapping":"{}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.models[0]").value("deepseek-chat"))
                .andExpect(jsonPath("$.data.models[1]").value("deepseek-flash"))
                .andReturn();
        log.info("渠道视图契约响应: {}", created.getResponse().getContentAsString());
    }

    /**
     * 日志管理面视图：stream/isRetry/sanitized 由整数转换为布尔。
     */
    @Test
    public void request_log_view_booleans() throws Exception {
        requestLogCase.record(RequestLog.builder()
                .model("bool-view-model")
                .stream(1)
                .isRetry(1)
                .sanitized(1)
                .statusCode(200)
                .build());

        MvcResult result = mockMvc.perform(get("/admin/api/logs")
                        .param("model", "bool-view-model")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].isStream").value(true))
                .andExpect(jsonPath("$.data.records[0].isRetry").value(true))
                .andExpect(jsonPath("$.data.records[0].sanitized").value(true))
                .andReturn();
        log.info("日志布尔视图契约响应: {}", result.getResponse().getContentAsString());
    }

    /**
     * 内置规则启停契约：PUT /admin/api/security/rules/builtin/{ruleId} {enabled}。
     */
    @Test
    public void builtin_rule_toggle_by_rule_id() throws Exception {
        MvcResult list = mockMvc.perform(get("/admin/api/security/rules/builtin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleId").isNotEmpty())
                .andReturn();
        JSONObject body = JSON.parseObject(list.getResponse().getContentAsString());
        String ruleId = body.getJSONArray("data").getJSONObject(0).getString("ruleId");
        assertTrue("内置规则 ruleId 不应为空", ruleId != null && !ruleId.isBlank());

        MvcResult result = mockMvc.perform(put("/admin/api/security/rules/builtin/{ruleId}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andReturn();
        log.info("内置规则启停契约响应: {}", result.getResponse().getContentAsString());
    }

    /**
     * 数据面 CORS 预检：OPTIONS 请求必须由 CORS 处理器接管（不被鉴权 Filter 拦 401）。
     */
    @Test
    public void data_plane_cors_preflight_passes() throws Exception {
        mockMvc.perform(options("/v1/chat/completions")
                        .header("Origin", "http://localhost:63342")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:63342"))
                .andReturn();
    }

    /**
     * 数据面跨源调用：带有效密钥的 POST 不再因预检/鉴权返回 invalid_api_key。
     */
    @Test
    public void data_plane_cors_chat_completion_passes() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/api/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"跨源测试密钥","key":"sk-cors-test-001","status":1,"quotaLimit":10000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("sk-cors-test-001"))
                .andReturn();
        log.info("跨源测试密钥创建响应: {}", created.getResponse().getContentAsString());

        MvcResult result = mockMvc.perform(post("/v1/chat/completions")
                        .header("Origin", "http://localhost:63342")
                        .header("Authorization", "Bearer sk-cors-test-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"deepseek-chat\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andReturn();
        int httpStatus = result.getResponse().getStatus();
        assertTrue("跨源数据面调用不应被鉴权拒绝(401)，实际=" + httpStatus, httpStatus != 401);
        String content = result.getResponse().getContentAsString();
        assertTrue("不应出现鉴权失败响应", !content.contains("invalid_api_key"));
        log.info("数据面跨源调用响应(status={}): {}", httpStatus, content);
    }
}
