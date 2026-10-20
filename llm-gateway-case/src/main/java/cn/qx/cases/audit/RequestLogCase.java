package cn.qx.cases.audit;

import cn.qx.domain.audit.adapter.repository.IRequestLogRepository;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.types.common.TimeUtils;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 请求日志审计用例。
 */
@Slf4j
@Service
public class RequestLogCase {

    private static final int MAX_PAGE_SIZE = 200;

    @Resource
    private IRequestLogRepository requestLogRepository;

    /** 追加一条不可变审计日志。 */
    public RequestLog record(RequestLog requestLog) {
        if (requestLog == null || isBlank(requestLog.getModel())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "日志 model 不能为空");
        }
        requestLog.setId(isBlank(requestLog.getId()) ? TimeUtils.uuid() : requestLog.getId());
        requestLog.setCreatedAt(isBlank(requestLog.getCreatedAt())
                ? TimeUtils.now()
                : TimeUtils.normalize(requestLog.getCreatedAt()));
        requestLog.setStream(requestLog.getStream() == null ? 0 : requestLog.getStream());
        requestLog.setIsRetry(requestLog.getIsRetry() == null ? 0 : requestLog.getIsRetry());
        requestLog.setPromptTokens(requestLog.getPromptTokens() == null ? 0L : requestLog.getPromptTokens());
        requestLog.setCompletionTokens(
                requestLog.getCompletionTokens() == null ? 0L : requestLog.getCompletionTokens());
        requestLog.setTotalTokens(requestLog.getTotalTokens() == null ? 0L : requestLog.getTotalTokens());
        requestLog.setDurationMs(requestLog.getDurationMs() == null ? 0L : requestLog.getDurationMs());
        requestLog.setRiskLevel(isBlank(requestLog.getRiskLevel()) ? "Clean" : requestLog.getRiskLevel());
        requestLog.setRiskScore(requestLog.getRiskScore() == null ? 0 : requestLog.getRiskScore());
        requestLog.setSecurityAction(
                isBlank(requestLog.getSecurityAction()) ? "Allow" : requestLog.getSecurityAction());
        requestLog.setSanitized(requestLog.getSanitized() == null ? 0 : requestLog.getSanitized());
        requestLogRepository.insert(requestLog);
        log.info("请求日志已追加: {}", JSON.toJSONString(requestLog));
        return requestLog;
    }

    @Transactional(readOnly = true)
    public RequestLogPageResult page(RequestLogQuery query) {
        RequestLogQuery normalized = normalize(query);
        normalized.setCreatedFrom(TimeUtils.normalize(normalized.getCreatedFrom()));
        normalized.setCreatedTo(TimeUtils.normalize(normalized.getCreatedTo()));
        long total = requestLogRepository.count(normalized);
        List<RequestLog> records = total == 0
                ? List.of()
                : requestLogRepository.page(normalized);
        return RequestLogPageResult.builder()
                .records(records)
                .total(total)
                .page(normalized.getPage())
                .size(normalized.getSize())
                .build();
    }

    public RequestLog get(String id) {
        RequestLog requestLog = requestLogRepository.getById(id);
        if (requestLog == null) {
            throw new AppException(ResponseCode.NOT_FOUND.getCode(), "请求日志不存在: " + id);
        }
        return requestLog;
    }

    public void delete(String id) {
        requestLogRepository.deleteById(id);
    }

    public int deleteBefore(String createdAt) {
        if (isBlank(createdAt)) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "清理时间阈值不能为空");
        }
        String normalized = TimeUtils.normalize(createdAt);
        int deleted = requestLogRepository.deleteBefore(normalized);
        log.info("已清理 {} 条请求日志, created_at < {}", deleted, normalized);
        return deleted;
    }

    public int clearAll() {
        int cleared = requestLogRepository.clearAll();
        log.info("已清空请求日志 {} 条", cleared);
        return cleared;
    }

    private static RequestLogQuery normalize(RequestLogQuery query) {
        RequestLogQuery value = query == null ? new RequestLogQuery() : query;
        value.setPage(Math.max(1, value.getPage()));
        value.setSize(Math.min(MAX_PAGE_SIZE, Math.max(1, value.getSize())));
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
