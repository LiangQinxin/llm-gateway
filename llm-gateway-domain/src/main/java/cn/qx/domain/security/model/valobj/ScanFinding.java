package cn.qx.domain.security.model.valobj;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次规则命中。evidence 为内部定位用的完整原文，不参与 JSON 序列化；
 * evidenceMasked 为对外展示的打码证据（审计日志不泄露原文）。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ScanFinding {

    /** 命中规则 ID（与子扫描器代码 id 一致）。 */
    private String ruleId;
    /** 命中的子扫描器名称（如 credential/path/network/unicode）。 */
    private String scanner;
    /** 风险类别标签，用于组合加分与归因。 */
    private List<SecurityCategory> categories;
    /** 该发现的严重度（基础分取本次扫描最高 severity）。 */
    private SecuritySeverity severity;
    /** 人读发现标题。 */
    private String title;
    /** 发现的详细说明。 */
    private String description;

    /** 完整证据原文，仅内部去重/定位使用，序列化时排除。 */
    @JSONField(serialize = false)
    private String evidence;

    /** 打码证据，对外展示，如 sk-ant-a****x123。 */
    private String evidenceMasked;

    /** 命中位置（JSONPath 或原文偏移区间）。 */
    private String location;
    /** 对外展示的命中消息。 */
    private String message;
    /** 是否可脱敏（true 时允许 REDACT 打码）。 */
    private boolean redactable;
    /** 该发现经策略判定后的发现级动作。 */
    private FindingAction action;
}
