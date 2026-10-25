package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全发现管理面展示值对象（安全审计列表）。
 * <p>evidence 只暴露打码值，不返回原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityFindingRecord {

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
    /** 严重度（info/low/medium/high/critical） */
    private String severity;
    /** 规则标题 */
    private String title;
    /** 规则描述 */
    private String description;
    /** 命中位置 */
    private String location;
    /** 打码证据 */
    private String evidenceMasked;
    /** 处理动作（allow/warn/redact/block） */
    private String action;
    /** 创建时间 */
    private String createdAt;
}
