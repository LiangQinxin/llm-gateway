package cn.qx.cases.security;

import cn.qx.api.ISecurityAuditCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.cases.proxy.ProxyException;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import cn.qx.domain.security.model.valobj.AuditResult;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.ScanRequest;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecurityAction;
import cn.qx.domain.security.service.ISecurityScanner;
import cn.qx.domain.security.service.redact.IRecursiveRedactorService;
import cn.qx.types.common.TimeUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 请求侧安全审计编排：扫描 -> 阻断/脱敏 -> 风险路径留痕。
 */
@Slf4j
@Service
public class SecurityAuditCase implements ISecurityAuditCase {

    @Resource
    private ISecurityScanner securityScanner;

    @Resource
    private IRecursiveRedactorService recursiveRedactor;

    @Resource
    private IRequestLogCase requestLogCase;

    @Resource
    private ISecurityFindingRepository securityFindingRepository;

    @Resource
    private ScanSettings securityScanSettings;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public String auditRequest(String body, String model, String inboundPath) {
        if (body == null || body.isBlank()
                || securityScanSettings == null || !securityScanSettings.isEnabled()) {
            return body;
        }
        AuditResult result = securityScanner.scan(ScanRequest.builder()
                .body(body)
                .phase(ScanPhase.REQUEST)
                .settings(securityScanSettings)
                .build());
        if (result.getFinalAction() == SecurityAction.BLOCK) {
            persistRiskPath(body, model, inboundPath, result, false);
            throw new ProxyException(403, "security_blocked",
                    "invalid_request_error", result.getSummary());
        }
        if (result.getFinalAction() == SecurityAction.SANITIZE) {
            String sanitized = recursiveRedactor.redact(body, securityScanSettings);
            persistRiskPath(body, model, inboundPath, result, true);
            log.info("安全审计已脱敏请求: inboundPath={}, model={}", inboundPath, model);
            return sanitized;
        }
        return body;
    }

    private void persistRiskPath(String originalBody,
                                 String model,
                                 String inboundPath,
                                 AuditResult result,
                                 boolean sanitized) {
        try {
            String now = TimeUtils.now();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                RequestLog requestLog = requestLogCase.record(RequestLog.builder()
                        .model(resolveModel(model, originalBody))
                        .mode(modeOf(inboundPath))
                        .protocolType("http")
                        .stream(0)
                        .riskLevel(levelName(result))
                        .riskScore(result.getScore())
                        .riskSummary(result.getSummary())
                        .securityAction(actionName(result.getFinalAction()))
                        .sanitized(sanitized ? 1 : 0)
                        .blockedReason(result.isBlocked() ? result.getSummary() : null)
                        .requestBody(originalBody)
                        .traceId(TimeUtils.uuid())
                        .build());
                securityFindingRepository.insertAll(
                        requestLog.getId(), ScanPhase.REQUEST, result.getFindings(), now);
                log.info("安全风险路径已留痕: logId={}, action={}, findings={}, sanitized={}",
                        requestLog.getId(), result.getFinalAction(),
                        result.getFindings().size(), sanitized);
            });
        } catch (Exception e) {
            log.warn("安全风险路径留痕失败（best-effort）: action={}, error={}",
                    result.getFinalAction(), e.getMessage());
        }
    }

    private static String levelName(AuditResult result) {
        String value = result.getLevel().name().toLowerCase();
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String actionName(SecurityAction action) {
        return switch (action) {
            case SANITIZE -> "Sanitize";
            case BLOCK -> "Block";
            default -> "Allow";
        };
    }

    private static String modeOf(String inboundPath) {
        if (inboundPath == null) {
            return "chat";
        }
        if (inboundPath.contains("embedding")) {
            return "embedding";
        }
        if (inboundPath.contains("completion") || inboundPath.contains("responses")) {
            return "completion";
        }
        return "chat";
    }

    private static String resolveModel(String model, String body) {
        if (!isBlank(model)) {
            return model;
        }
        try {
            JSONObject parsed = JSON.parseObject(body);
            String parsedModel = parsed == null ? null : parsed.getString("model");
            return isBlank(parsedModel) ? "unknown" : parsedModel;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
