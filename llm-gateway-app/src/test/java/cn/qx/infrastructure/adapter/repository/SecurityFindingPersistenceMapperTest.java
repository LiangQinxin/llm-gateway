package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.model.valobj.FindingAction;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecurityCategory;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.infrastructure.adapter.repository.SecurityFindingPersistenceMapper;
import cn.qx.infrastructure.dao.po.SecurityFindingPO;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ScanFinding → SecurityFindingPO 持久化映射测试。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class SecurityFindingPersistenceMapperTest {

    /** 被测的持久化映射器：断言 ScanFinding → SecurityFindingPO 的类别/打码/哈希转换。 */
    @Resource
    private SecurityFindingPersistenceMapper mapper;

    @Test
    public void maps_categories_masked_evidence_hash_and_action() {
        ScanFinding finding = ScanFinding.builder()
                .ruleId("credential_password")
                .scanner("credential")
                .categories(List.of(SecurityCategory.CREDENTIAL, SecurityCategory.EXFIL))
                .severity(SecuritySeverity.HIGH)
                .title("Password Assignment")
                .description("credential")
                .location("@raw[0..20]")
                .evidence("password=abc12345 sk-proj-abcdefghijklmnopqrstuvwx\u200B")
                .action(FindingAction.WARN)
                .build();
        SecurityFindingPO po = mapper.toPO(
                "log-1", ScanPhase.REQUEST, finding, "2026-10-23T20:00:00+08:00");
        log.info("安全发现 PO: {}", JSON.toJSONString(po));

        assertNotNull(po.getId());
        assertEquals("credential,exfil", po.getCategory());
        assertEquals("warn", po.getAction());
        assertEquals("request", po.getPhase());
        assertFalse(po.getEvidenceMasked().contains("abc12345"));
        assertFalse(po.getEvidenceMasked().contains("sk-proj"));
        assertFalse(po.getEvidenceMasked().contains("\u200B"));
        assertEquals(64, po.getEvidenceHash().length());
        assertTrue(po.getEvidenceHash().matches("[0-9a-f]{64}"));
    }

    /**
     * 自定义黑名单 finding 无组合类别时仍以 custom 入库。
     */
    @Test
    public void maps_custom_rule_category() {
        ScanFinding finding = ScanFinding.builder()
                .ruleId("custom.blacklist.keyword")
                .scanner("custom")
                .categories(List.of())
                .severity(SecuritySeverity.HIGH)
                .title("Custom rule")
                .description("custom blacklist")
                .location("$.messages[0].content")
                .evidence("forbidden-marker")
                .action(FindingAction.BLOCK)
                .build();
        SecurityFindingPO po = mapper.toPO(
                "log-custom", ScanPhase.REQUEST, finding, "2026-10-24T20:00:00+08:00");
        log.info("自定义规则发现 PO: {}", JSON.toJSONString(po));

        assertEquals("custom", po.getCategory());
        assertEquals("block", po.getAction());
    }
}
