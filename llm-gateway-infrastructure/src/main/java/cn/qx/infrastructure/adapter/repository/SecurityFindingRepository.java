package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.adapter.repository.ISecurityFindingRepository;
import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanPhase;
import cn.qx.infrastructure.dao.SecurityFindingMapper;
import cn.qx.infrastructure.dao.po.SecurityFindingPO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 安全发现批量落库适配器；证据只写打码值与哈希。
 */
@Repository
public class SecurityFindingRepository implements ISecurityFindingRepository {

    @Resource
    private SecurityFindingMapper mapper;

    @Resource
    private SecurityFindingPersistenceMapper persistenceMapper;

    @Override
    public void insertAll(String logId,
                          ScanPhase phase,
                          List<ScanFinding> findings,
                          String createdAt) {
        if (findings == null || findings.isEmpty()) {
            return;
        }
        List<SecurityFindingPO> items = findings.stream()
                .map(finding -> persistenceMapper.toPO(logId, phase, finding, createdAt))
                .toList();
        mapper.insertAll(items);
    }

    @Override
    public long countByLogId(String logId) {
        return mapper.countByLogId(logId);
    }
}
