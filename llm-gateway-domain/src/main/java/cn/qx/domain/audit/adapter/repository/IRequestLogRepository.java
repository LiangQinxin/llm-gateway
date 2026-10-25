package cn.qx.domain.audit.adapter.repository;

import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.domain.audit.model.valobj.RequestLogStats;

import java.util.List;

/**
 * 请求日志仓储端口。
 */
public interface IRequestLogRepository {

    /** 插入一条请求日志。 */
    void insert(RequestLog requestLog);

    /** 按 ID 查询请求日志，不存在返回 null。 */
    RequestLog getById(String id);

    /** 按查询条件统计日志条数。 */
    long count(RequestLogQuery query);

    /** 按查询条件分页查询日志列表。 */
    List<RequestLog> page(RequestLogQuery query);

    /** 聚合统计：总请求数、总 token、平均耗时、错误请求数。 */
    RequestLogStats stats();

    /** 按 ID 删除请求日志，返回影响行数。 */
    int deleteById(String id);

    /** 删除创建时间早于指定时间点的日志，返回影响行数。 */
    int deleteBefore(String createdAt);

    /** 清空全部请求日志，返回影响行数。 */
    int clearAll();
}
