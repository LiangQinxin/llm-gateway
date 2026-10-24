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

    /** 批量插入安全发现记录（foreach 展开多值 VALUES）。 */
    int insertAll(@Param("items") List<SecurityFindingPO> items);

    /** 按请求日志 id 统计安全发现条数。 */
    long countByLogId(@Param("logId") String logId);
}
