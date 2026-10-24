package cn.qx.cases.proxy;

import cn.qx.api.IRequestLogCase;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * ProxyCase 挂载安全审计的真实链路测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "security.audit.mode=block")
public class ProxySecurityAuditTest {

    @Resource
    private ProxyCase proxyCase;

    @Resource
    private IRequestLogCase requestLogCase;

    @Resource
    private ISecurityFindingRepository securityFindingRepository;

    /**
     * 风险请求在 prepare 阶段被拒绝，不会触达渠道分发。
     */
    @Test
    @Transactional
    public void prepare_blocks_risky_body_before_forwarding() {
        String model = "audit-proxy-" + UUID.randomUUID();
        String body = "{\"model\":\"" + model
                + "\",\"stream\":false,\"messages\":[{\"role\":\"user\","
                + "\"content\":\"AKIA1234567890ABCDEF\"}]}";

        ProxyException error = assertThrows(ProxyException.class,
                () -> proxyCase.prepare(body, "application/json", "/v1/chat/completions"));
        log.info("ProxyCase 安全阻断: status={}, code={}", error.getStatusCode(), error.getCode());
        assertEquals(403, error.getStatusCode());
        assertEquals("security_blocked", error.getCode());

        List<RequestLog> records = requestLogCase.page(RequestLogQuery.builder()
                .model(model)
                .page(1)
                .size(10)
                .build()).getRecords();
        log.info("ProxyCase 风险日志: {}", JSON.toJSONString(records));
        assertEquals(1, records.size());
        assertEquals("Block", records.get(0).getSecurityAction());
        assertTrue(securityFindingRepository.countByLogId(records.get(0).getId()) > 0);
    }
}
