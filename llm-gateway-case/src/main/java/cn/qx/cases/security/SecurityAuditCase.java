package cn.qx.cases.security;

import cn.qx.api.ISecurityAuditCase;
import cn.qx.api.dto.security.SecurityAuditOutcome;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.service.ISecurityScanner;
import cn.qx.domain.security.service.redact.IRecursiveRedactorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 请求侧安全审计纯决策：扫描 -> 阻断/脱敏判定 -> 生成转发体与脱敏落库体。
 */
@Slf4j
@Service
public class SecurityAuditCase implements ISecurityAuditCase {

    /** 安全扫描引擎端口 */
    @Resource
    private ISecurityScanner securityScanner;

    /** 请求体递归脱敏器端口 */
    @Resource
    private IRecursiveRedactorService recursiveRedactor;

    /** 全局安全扫描配置 */
    @Resource
    private ScanSettings securityScanSettings;

    @Override
    public SecurityAuditOutcome auditRequest(String body, String model, String inboundPath) {
        if (body == null || body.isBlank()
                || securityScanSettings == null || !securityScanSettings.isEnabled()) {
            return clean(body);
        }
        AuditResult result = securityScanner.scan(ScanRequest.builder()
                .body(body)
                .phase(ScanPhase.REQUEST)
                .settings(securityScanSettings)
                .build());

        String auditBody = hasFindings(result) ? redactOrNull(body) : body;
        if (result.getFinalAction() == SecurityAction.BLOCK) {
            return SecurityAuditOutcome.builder()
                    .forwardBody(body)
                    .auditBody(auditBody)
                    .audit(result)
                    .blocked(true)
                    .build();
        }
        if (result.getFinalAction() == SecurityAction.SANITIZE) {
            String sanitized = redactOrNull(body);
            log.info("安全审计已脱敏请求: inboundPath={}, model={}", inboundPath, model);
            return SecurityAuditOutcome.builder()
                    .forwardBody(sanitized == null ? body : sanitized)
                    .auditBody(sanitized)
                    .audit(result)
                    .blocked(false)
                    .build();
        }
        return SecurityAuditOutcome.builder()
                .forwardBody(body)
                .auditBody(auditBody)
                .audit(result)
                .blocked(false)
                .build();
    }

    private static SecurityAuditOutcome clean(String body) {
        return SecurityAuditOutcome.builder()
                .forwardBody(body)
                .auditBody(body)
                .audit(AuditResult.clean(ScanPhase.REQUEST, "security audit disabled"))
                .blocked(false)
                .build();
    }

    private String redactOrNull(String body) {
        try {
            return recursiveRedactor.redact(body, securityScanSettings);
        } catch (Exception e) {
            log.warn("请求体递归脱敏失败，正文不落库: {}", e.getMessage());
            return null;
        }
    }

    private static boolean hasFindings(AuditResult result) {
        return result != null && result.getFindings() != null && !result.getFindings().isEmpty();
    }
}
