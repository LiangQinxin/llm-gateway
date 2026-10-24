package cn.qx.cases.audit;

import cn.qx.api.IDataPlaneAuditCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.audit.DataPlaneLogData;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * request-log.policy=basic 真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "request-log.policy=basic")
public class DataPlaneAuditBasicPolicyTest {

    @Resource
    private IDataPlaneAuditCase dataPlaneAuditCase;

    @Resource
    private IRequestLogCase requestLogCase;

    /**
     * basic 档不落请求/响应正文，但索引、状态与用量字段照常保留。
     */
    @Test
    public void basic_policy_drops_request_and_response_bodies() {
        String model = "audit-basic-" + UUID.randomUUID();
        try {
            dataPlaneAuditCase.recordDataPlane(DataPlaneLogData.builder()
                    .model(model)
                    .mode("chat")
                    .protocolType("http")
                    .stream(0)
                    .isRetry(1)
                    .statusCode(200)
                    .totalTokens(9L)
                    .requestBody("{\"messages\":[{\"role\":\"user\",\"content\":\"secret\"}]}")
                    .responseChoices("[{\"message\":{\"content\":\"answer\"}}]")
                    .traceId(UUID.randomUUID().toString())
                    .build(), List.of());

            List<RequestLog> logs = requestLogCase.page(RequestLogQuery.builder()
                    .model(model).page(1).size(20).build()).getRecords();
            log.info("basic policy 日志: model={}, logs={}", model, logs.size());
            assertEquals(1, logs.size());
            assertNull("basic 不落请求正文", logs.get(0).getRequestBody());
            assertNull("basic 不落响应正文", logs.get(0).getResponseChoices());
            assertEquals(Integer.valueOf(200), logs.get(0).getStatusCode());
            assertEquals(Long.valueOf(9), logs.get(0).getTotalTokens());
        } finally {
            requestLogCase.page(RequestLogQuery.builder()
                            .model(model).page(1).size(20).build())
                    .getRecords()
                    .forEach(log -> requestLogCase.delete(log.getId()));
        }
    }
}
