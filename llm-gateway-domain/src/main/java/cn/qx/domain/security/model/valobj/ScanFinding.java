package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一次规则命中。
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
    private String evidence;
    private String location;
    private String message;
    private boolean redactable;
    private FindingAction action;
}
