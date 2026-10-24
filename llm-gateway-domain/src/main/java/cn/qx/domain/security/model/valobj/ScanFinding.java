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

    private String ruleId;
    private String scanner;
    private List<SecurityCategory> categories;
    private SecuritySeverity severity;
    private String title;
    private String description;

    /** 完整证据原文，仅内部去重/定位使用，序列化时排除。 */
    @JSONField(serialize = false)
    private String evidence;

    /** 打码证据，对外展示，如 sk-ant-a****x123。 */
    private String evidenceMasked;

    private String location;
    private String message;
    private boolean redactable;
    private FindingAction action;
}
