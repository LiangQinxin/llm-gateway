package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;

import java.util.List;

/**
 * 安全发现持久化端口。
 */
public interface ISecurityFindingRepository {

    void insertAll(String logId,
                   ScanPhase phase,
                   List<ScanFinding> findings,
                   String createdAt);

    long countByLogId(String logId);
}
