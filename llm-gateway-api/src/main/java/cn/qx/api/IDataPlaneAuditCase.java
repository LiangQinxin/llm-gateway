package cn.qx.api;

import cn.qx.api.dto.audit.DataPlaneLogData;
import cn.qx.domain.security.model.valobj.ScanFinding;

import java.util.List;

/**
 * 数据面审计留痕用例（audit 域单写者）。
 */
public interface IDataPlaneAuditCase {

    /**
     * 同一事务写入 RequestLog 与安全发现明细；任何失败静默化，不影响数据面主流程。
     */
    void recordDataPlane(DataPlaneLogData logData, List<ScanFinding> findings);
}
