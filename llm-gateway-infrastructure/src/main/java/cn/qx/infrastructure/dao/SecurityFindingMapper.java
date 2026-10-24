package cn.qx.infrastructure.dao;

import cn.qx.infrastructure.dao.po.SecurityFindingPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * security_audit_finding 表 Mapper。
 */
@Mapper
public interface SecurityFindingMapper {

    int insertAll(@Param("items") List<SecurityFindingPO> items);

    long countByLogId(@Param("logId") String logId);
}
