package cn.qx.cases.audit;

import cn.qx.api.dto.audit.RequestLogPageResult;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * RequestLogCase 真实链路测试。
 * <p>事务内执行，测试结束回滚，避免清理本地库既有审计数据。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
@Transactional
public class RequestLogCaseTest {

    /** 请求日志用例 */
    @Resource
    private RequestLogCase requestLogCase;

    @Test
    public void request_log_append_page_detail_and_filter() {
        RequestLog older = requestLogCase.record(RequestLog.builder()
                .apiKeyId("key-test-1").apiKeyName("测试密钥")
                .channelId("channel-test-1").channelName("测试渠道")
                .model("deepseek-flash").upstreamModel("deepseek-flash")
                .mode("chat").protocolType("openai").stream(0).isRetry(0)
                .statusCode(200).promptTokens(10L).completionTokens(20L).totalTokens(30L)
                .durationMs(100L).riskLevel("Clean").riskScore(0)
                .securityAction("Allow").sanitized(0)
                .requestBody("{\"model\":\"deepseek-flash\"}")
                .responseChoices("[{\"index\":0}]")
                .createdAt("2026-10-01T10:00:00.000000+08:00")
                .build());
        RequestLog newer = requestLogCase.record(RequestLog.builder()
                .apiKeyId("key-test-1").apiKeyName("测试密钥")
                .channelId("channel-test-1").channelName("测试渠道")
                .model("gpt-4o").upstreamModel("deepseek-flash")
                .mode("chat").protocolType("openai").stream(1).isRetry(0)
                .statusCode(500).promptTokens(1L).completionTokens(2L).totalTokens(3L)
                .durationMs(200L).riskLevel("Low").riskScore(10)
                .securityAction("Sanitize").sanitized(1)
                .requestBody("{\"model\":\"gpt-4o\"}")
                .responseChoices("[]")
                .createdAt("2026-10-02T10:00:00.000000+08:00")
                .build());
        log.info("追加日志: older={}, newer={}", JSON.toJSONString(older), JSON.toJSONString(newer));

        assertNotNull("seq 应自增回填", older.getSeq());
        assertNotNull("seq 应自增回填", newer.getSeq());
        assertTrue("后写日志 seq 应更大", newer.getSeq() > older.getSeq());

        RequestLogQuery query = RequestLogQuery.builder()
                .apiKeyId("key-test-1")
                .channelId("channel-test-1")
                .model("gpt-4o")
                .statusCode(500)
                .createdFrom("2026-10-02T00:00:00.000000+08:00")
                .createdTo("2026-10-02T23:59:59.999999+08:00")
                .page(1).size(10)
                .build();
        RequestLogPageResult page = requestLogCase.page(query);
        log.info("分页查询结果: {}", JSON.toJSONString(page));
        assertEquals("应命中一条过滤日志", 1, page.getTotal());
        assertEquals("命中的应是 newer", newer.getId(), page.getRecords().get(0).getId());

        RequestLog detail = requestLogCase.get(newer.getId());
        assertEquals("详情应含 request_body", "{\"model\":\"gpt-4o\"}", detail.getRequestBody());
        assertNotNull("详情应可解析 request_body", detail.requestBodyJson());

        List<RequestLog> beforeCleanup = requestLogCase.page(RequestLogQuery.builder()
                .apiKeyId("key-test-1").page(1).size(10).build()).getRecords();
        assertEquals("清理前应有两条", 2, beforeCleanup.size());
        int deleted = requestLogCase.deleteBefore("2026-10-02T00:00:00.000000+08:00");
        assertTrue("清理应删除旧日志", deleted >= 1);
        assertEquals("新日志应保留", newer.getId(),
                requestLogCase.page(RequestLogQuery.builder()
                        .apiKeyId("key-test-1").page(1).size(10).build())
                        .getRecords().get(0).getId());
    }

    @Test
    public void clear_all_is_available_and_rolls_back() {
        requestLogCase.record(RequestLog.builder()
                .model("clear-test")
                .apiKeyId("clear-key")
                .channelId("clear-channel")
                .statusCode(200)
                .createdAt("2026-10-03T10:00:00.000000+08:00")
                .build());
        int cleared = requestLogCase.clearAll();
        assertTrue("清空应影响至少一条", cleared >= 1);
        assertEquals("清空后分页为空", 0, requestLogCase.page(RequestLogQuery.builder()
                .model("clear-test").page(1).size(10).build()).getTotal());
    }
}
