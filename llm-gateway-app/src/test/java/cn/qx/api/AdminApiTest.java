package cn.qx.api;

import cn.qx.cases.audit.RequestLogCase;
import cn.qx.domain.audit.model.entity.RequestLog;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理面 Controller 契约测试。真实 Spring 容器 + 本地 MySQL，事务回滚。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AdminApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Resource
    private RequestLogCase requestLogCase;

    @Test
    public void channel_admin_api_crud_and_toggle() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/api/channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"API测试渠道",
                                  "type":"deepseek",
                                  "baseUrl":"http://127.0.0.1:8777/v1",
                                  "apiKey":"sk-test",
                                  "models":"[\\"deepseek-flash\\"]",
                                  "priority":50,
                                  "weight":2,
                                  "config":"{}",
                                  "modelMapping":"{}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andReturn();
        String id = data(created).getString("id");
        log.info("渠道 API 创建结果: {}", created.getResponse().getContentAsString());

        mockMvc.perform(get("/admin/api/channels/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(put("/admin/api/channels/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"API测试渠道-修改",
                                  "type":"deepseek",
                                  "baseUrl":"http://127.0.0.1:8777/v1",
                                  "apiKey":"sk-test",
                                  "models":"[\\"deepseek-flash\\"]",
                                  "status":1,
                                  "priority":60,
                                  "weight":2,
                                  "config":"{}",
                                  "modelMapping":"{}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("API测试渠道-修改"));

        mockMvc.perform(post("/admin/api/channels/{id}/toggle", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(delete("/admin/api/channels/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    public void api_key_admin_api_crud_and_toggle() throws Exception {
        MvcResult created = mockMvc.perform(post("/admin/api/keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"API测试密钥",
                                  "allowedModels":"[\\"deepseek-flash\\"]",
                                  "allowedChannels":"[]",
                                  "quotaLimit":100
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andReturn();
        JSONObject data = data(created);
        log.info("密钥 API 创建结果: {}", created.getResponse().getContentAsString());

        mockMvc.perform(get("/admin/api/keys/{id}", data.getString("id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("API测试密钥"));

        mockMvc.perform(post("/admin/api/keys/{id}/toggle", data.getString("id"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        mockMvc.perform(delete("/admin/api/keys/{id}", data.getString("id")))
                .andExpect(status().isOk());
    }

    @Test
    public void request_log_admin_api_page_detail_and_cleanup() throws Exception {
        RequestLog log = requestLogCase.record(RequestLog.builder()
                .model("api-log-test")
                .apiKeyId("api-key")
                .channelId("api-channel")
                .statusCode(200)
                .requestBody("{\"model\":\"api-log-test\"}")
                .responseChoices("[]")
                .createdAt("2026-10-04T10:00:00.000000+08:00")
                .build());
        assertNotNull(log.getId());

        mockMvc.perform(get("/admin/api/logs")
                        .param("model", "api-log-test")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(log.getId()));

        mockMvc.perform(get("/admin/api/logs/{id}", log.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestBody").value("{\"model\":\"api-log-test\"}"));

        mockMvc.perform(delete("/admin/api/logs")
                        .param("before", "2026-10-05T00:00:00.000000+08:00"))
                .andExpect(status().isOk());
        assertEquals("清理后测试日志应删除", 0,
                requestLogCase.page(cn.qx.domain.audit.model.valobj.RequestLogQuery.builder()
                        .model("api-log-test").page(1).size(10).build()).getTotal());

        requestLogCase.record(RequestLog.builder()
                .model("api-log-clear-test")
                .statusCode(200)
                .build());
        mockMvc.perform(delete("/admin/api/logs/all"))
                .andExpect(status().isOk());
        assertEquals("清空接口应删除全部日志", 0L,
                requestLogCase.page(cn.qx.domain.audit.model.valobj.RequestLogQuery.builder()
                        .page(1).size(10).build()).getTotal());
    }

    private static JSONObject data(MvcResult result) throws Exception {
        JSONObject response = JSON.parseObject(result.getResponse().getContentAsString());
        return response.getJSONObject("data");
    }
}
