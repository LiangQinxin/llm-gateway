package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.domain.security.model.valobj.SecurityFindingRecord;

import java.util.List;

/**
 * 安全发现持久化端口。
 */
public interface ISecurityFindingRepository {

    /** 按请求日志 ID 批量插入安全发现记录（一次扫描的全部 finding 同写）。 */
    void insertAll(String logId,
                   ScanPhase phase,
                   List<ScanFinding> findings,
                   String createdAt);

    /** 按请求日志 ID 统计关联的安全发现条数。 */
    long countByLogId(String logId);

    /** 按创建时间倒序取最近 N 条安全发现（管理面审计列表）。 */
    List<SecurityFindingRecord> latest(int limit);
}
