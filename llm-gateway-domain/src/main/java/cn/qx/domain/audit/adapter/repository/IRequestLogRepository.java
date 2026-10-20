package cn.qx.domain.audit.adapter.repository;

import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;

import java.util.List;

/**
 * 请求日志仓储端口。
 */
public interface IRequestLogRepository {

    void insert(RequestLog requestLog);

    RequestLog getById(String id);

    long count(RequestLogQuery query);

    List<RequestLog> page(RequestLogQuery query);

    int deleteById(String id);

    int deleteBefore(String createdAt);

    int clearAll();
}
