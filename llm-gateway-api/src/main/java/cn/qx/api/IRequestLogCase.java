package cn.qx.api;

import cn.qx.api.dto.audit.RequestLogPageResult;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;

/**
 * 请求日志审计用例接口（api 根包，对齐「服务必须定义接口」约定）。
 */
public interface IRequestLogCase {

    /** 追加一条不可变审计日志。 */
    RequestLog record(RequestLog requestLog);

    /** 分页查询请求日志。 */
    RequestLogPageResult page(RequestLogQuery query);

    /** 查询单条请求日志。 */
    RequestLog get(String id);

    /** 删除单条请求日志。 */
    void delete(String id);

    /** 清理创建时间早于阈值的日志，返回删除条数。 */
    int deleteBefore(String createdAt);

    /** 清空全部请求日志，返回删除条数。 */
    int clearAll();
}
