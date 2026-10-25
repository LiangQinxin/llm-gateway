package cn.qx.api.dto.security;

import cn.qx.domain.security.model.valobj.SecurityFindingRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全发现管理面输出对象。
 * <p>与前端约定：severity 返回大写枚举名（CRITICAL/HIGH/...），title 作为规则展示名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityFindingResult {

    /** 发现 ID（UUID） */
    private String id;
    /** 关联请求日志 ID */
    private String logId;
    /** 扫描阶段：request / response */
    private String phase;
    /** 风险类别（逗号拼接） */
    private String category;
    /** 命中规则 ID */
    private String ruleId;
    /** 规则标题 */
    private String title;
    /** 严重度（大写枚举名：INFO/LOW/MEDIUM/HIGH/CRITICAL） */
    private String severity;
    /** 发现详情说明 */
    private String description;
    /** 命中位置 */
    private String location;
    /** 打码证据 */
    private String evidenceMasked;
    /** 处理动作（allow/warn/redact/block） */
    private String action;
    /** 创建时间 */
    private String createdAt;

    /** 领域记录 → 管理面输出对象。 */
    public static SecurityFindingResult from(SecurityFindingRecord record) {
        if (record == null) {
            return null;
        }
        return SecurityFindingResult.builder()
                .id(record.getId())
                .logId(record.getLogId())
                .phase(record.getPhase())
                .category(record.getCategory())
                .ruleId(record.getRuleId())
                .title(record.getTitle())
                .severity(record.getSeverity() == null ? "" : record.getSeverity().toUpperCase())
                .description(record.getDescription())
                .location(record.getLocation())
                .evidenceMasked(record.getEvidenceMasked())
                .action(record.getAction())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
