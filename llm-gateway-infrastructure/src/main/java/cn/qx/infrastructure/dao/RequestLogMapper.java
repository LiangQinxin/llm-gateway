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

    int insert(RequestLogPO requestLogPO);

    RequestLogPO queryById(@Param("id") String id);

    long count(@Param("q") RequestLogQuery query);

    List<RequestLogPO> queryPage(@Param("q") RequestLogQuery query);

    int deleteById(@Param("id") String id);

    int deleteBefore(@Param("createdAt") String createdAt);

    int clearAll();
}
