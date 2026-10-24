package cn.qx.cases.audit;

import cn.qx.api.IDataPlaneAuditCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.audit.DataPlaneLogData;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * request-log.policy=brief 与静默化真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "request-log.policy=brief")
public class DataPlaneAuditBriefPolicyTest {

    @Resource
    private IDataPlaneAuditCase dataPlaneAuditCase;

    @Resource
    private IRequestLogCase requestLogCase;

    /**
     * brief 档只保留请求 messages 最近 3 条，响应正文不动。
     */
    @Test
    public void brief_policy_keeps_latest_three_messages() {
        String model = "audit-brief-" + UUID.randomUUID();
        String requestBody = """
                {"model":"%s","messages":[
                  {"role":"user","content":"m1"},
                  {"role":"assistant","content":"m2"},
                  {"role":"user","content":"m3"},
                  {"role":"assistant","content":"m4"},
                  {"role":"user","content":"m5"}
                ]}
                """.formatted(model);
        try {
            dataPlaneAuditCase.recordDataPlane(DataPlaneLogData.builder()
                    .model(model)
                    .mode("chat")
                    .protocolType("http")
                    .stream(0)
                    .isRetry(0)
                    .statusCode(200)
                    .requestBody(requestBody)
                    .responseChoices("[]")
                    .traceId(UUID.randomUUID().toString())
                    .build(), List.of());

            List<RequestLog> logs = logsFor(model);
            log.info("brief policy 日志: {}", JSON.toJSONString(logs));
            assertEquals(1, logs.size());
            JSONObject loggedBody = logs.get(0).requestBodyJson();
            assertEquals("brief 只留最新 3 条", 3,
                    loggedBody.getJSONArray("messages").size());
            assertEquals("m3", loggedBody.getJSONArray("messages")
                    .getJSONObject(0).getString("content"));
            assertEquals("响应正文不应被 brief 裁剪", "[]", logs.get(0).getResponseChoices());
        } finally {
            deleteLogsFor(model);
        }
    }

    /**
     * 落库失败静默化：调用方不感知异常，主流程可继续。
     */
    @Test
    public void audit_write_failure_is_silent() {
        try {
            dataPlaneAuditCase.recordDataPlane(DataPlaneLogData.builder()
                    .model(null)
                    .traceId(UUID.randomUUID().toString())
                    .build(), List.of());
            assertTrue("写库失败应静默化，不向主流程抛错", true);
        } catch (Exception e) {
            log.error("静默化失败", e);
            fail("数据面审计失败不应抛异常: " + e.getMessage());
        }
    }

    private List<RequestLog> logsFor(String model) {
        return requestLogCase.page(RequestLogQuery.builder()
                .model(model)
                .page(1)
                .size(20)
                .build()).getRecords();
    }

    private void deleteLogsFor(String model) {
        logsFor(model).forEach(log -> requestLogCase.delete(log.getId()));
    }
}
