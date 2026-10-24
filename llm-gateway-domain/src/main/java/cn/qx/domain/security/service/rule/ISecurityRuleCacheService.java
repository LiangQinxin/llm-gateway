package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecuritySeverity;

import java.util.List;

/**
 * 规则元数据缓存领域服务端口。
 */
public interface ISecurityRuleCacheService {

    void refresh();

    RuleMetadata findBuiltin(String ruleId);

    List<RuleMetadata> listBuiltin();

    List<CustomRuleMeta> listCustom();

    List<CustomRuleMeta> listCustom(String ruleType);

    boolean isRuleEnabled(RuleMetadata rule, ScanSettings settings);

    SecuritySeverity effectiveSeverity(ScanFinding finding, RuleMetadata rule);
}
