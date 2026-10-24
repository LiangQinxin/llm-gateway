package cn.qx.cases.audit;

import cn.qx.api.IDataPlaneAuditCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.audit.DataPlaneLogData;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.types.common.TimeUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 数据面审计单写者：RequestLog 与安全发现明细同事务落库。
 */
@Slf4j
@Service
public class DataPlaneAuditCase implements IDataPlaneAuditCase {

    /** 请求日志用例 */
    @Resource
    private IRequestLogCase requestLogCase;

    /** 安全发现仓储 */
    @Resource
    private ISecurityFindingRepository securityFindingRepository;

    /** 编程式事务管理器 */
    @Resource
    private PlatformTransactionManager transactionManager;

    /** 日志正文保留档：detailed / brief / basic */
    @Value("${request-log.policy:detailed}")
    private String logPolicy;

    @Override
    public void recordDataPlane(DataPlaneLogData logData, List<ScanFinding> findings) {
        if (logData == null) {
            return;
        }
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                RequestLog requestLog = new RequestLog();
                BeanUtils.copyProperties(logData, requestLog);
                applyLogPolicy(requestLog);
                String now = TimeUtils.now();
                requestLog.setCreatedAt(now);
                requestLogCase.record(requestLog);
                List<ScanFinding> safeFindings = findings == null ? List.of() : findings;
                securityFindingRepository.insertAll(
                        requestLog.getId(), ScanPhase.REQUEST, safeFindings, now);
                log.info("数据面审计已落库: {}", JSON.toJSONString(requestLog));
            });
        } catch (Exception e) {
            log.warn("数据面审计落库失败（best-effort）: traceId={}, model={}, error={}",
                    logData.getTraceId(), logData.getModel(), e.getMessage());
        }
    }

    private void applyLogPolicy(RequestLog requestLog) {
        String policy = logPolicy == null ? "detailed" : logPolicy.trim().toLowerCase();
        if ("basic".equals(policy)) {
            requestLog.setRequestBody(null);
            requestLog.setResponseChoices(null);
            return;
        }
        if ("brief".equals(policy) && requestLog.getRequestBody() != null
                && !requestLog.getRequestBody().isBlank()) {
            try {
                JSONObject body = JSON.parseObject(requestLog.getRequestBody());
                JSONArray messages = body == null ? null : body.getJSONArray("messages");
                if (messages != null && messages.size() > 3) {
                    JSONArray latest = new JSONArray(messages.subList(messages.size() - 3, messages.size()));
                    body.put("messages", latest);
                    requestLog.setRequestBody(body.toJSONString());
                }
            } catch (Exception e) {
                log.warn("brief 日志裁剪失败，回退原样: traceId={}, error={}",
                        requestLog.getTraceId(), e.getMessage());
            }
        }
    }
}
