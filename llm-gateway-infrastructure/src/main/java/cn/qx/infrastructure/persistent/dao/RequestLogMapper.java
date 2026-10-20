package cn.qx.infrastructure.persistent.dao;

import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * request_logs 表 Mapper。
 */
@Mapper
public interface RequestLogMapper {

    int insert(RequestLog requestLog);

    RequestLog queryById(@Param("id") String id);

    long count(@Param("q") RequestLogQuery query);

    List<RequestLog> queryPage(@Param("q") RequestLogQuery query);

    int deleteById(@Param("id") String id);

    int deleteBefore(@Param("createdAt") String createdAt);

    int clearAll();
}
