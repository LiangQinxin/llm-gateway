package cn.qx.infrastructure.dao;

import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import cn.qx.infrastructure.dao.po.RequestLogPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * request_logs 表 Mapper。SQL 见 request_log_mapper.xml。
 * <p>持久化对象为 RequestLogPO（cn.qx.infrastructure.dao.po）；分页条件 RequestLogQuery 为领域查询条件。
 */
@Mapper
public interface RequestLogMapper {

    /** 新增一条请求日志，自增主键 seq 通过 useGeneratedKeys 回写 PO。 */
    int insert(RequestLogPO requestLogPO);

    /** 按主键 id 查询请求日志。 */
    RequestLogPO queryById(@Param("id") String id);

    /** 按查询条件（model/keyword/apiKeyId/channelId/statusCode/createdFrom~createdTo）统计条数。 */
    long count(@Param("q") RequestLogQuery query);

    /** 按查询条件分页查询，按 seq 倒序，LIMIT/OFFSET 取自条件。 */
    List<RequestLogPO> queryPage(@Param("q") RequestLogQuery query);

    /** 聚合统计：总请求数、总 token、平均耗时、错误请求数（status_code &gt;= 400）。 */
    cn.qx.domain.audit.model.valobj.RequestLogStats stats();

    /** 按主键删除请求日志，返回影响行数。 */
    int deleteById(@Param("id") String id);

    /** 删除 created_at 早于指定时间点的日志，返回影响行数。 */
    int deleteBefore(@Param("createdAt") String createdAt);

    /** 清空全部请求日志，返回影响行数。 */
    int clearAll();
}
