package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.ScanFinding;
import cn.qx.domain.security.model.valobj.ScanSettings;
import cn.qx.domain.security.model.valobj.SecuritySeverity;

import java.util.List;

/**
 * 规则元数据缓存领域服务端口。
 */
public interface ISecurityRuleCacheService {

    /** 全量刷新规则缓存；表异常时 fail-open 到代码出厂目录，不阻断扫描。 */
    void refresh();

    /** 按规则 ID 查内置规则元数据，未命中返回 null。 */
    RuleMetadata findBuiltin(String ruleId);

    /** 列出全部内置规则元数据副本（拷贝，防外部修改）。 */
    List<RuleMetadata> listBuiltin();

    /** 列出全部自定义黑白名单规则副本。 */
    List<CustomRuleMeta> listCustom();

    /** 按规则类型（blacklist/whitelist）列出自定义规则，类型为空时列出全部。 */
    List<CustomRuleMeta> listCustom(String ruleType);

    /** 双层开关判定规则是否生效：enabled=1 且（toggleKey 为空或对应类别开关开启）。 */
    boolean isRuleEnabled(RuleMetadata rule, ScanSettings settings);

    /** 计算规则覆盖后的实际严重度；动态规则只升不降，其余以表值覆盖。 */
    SecuritySeverity effectiveSeverity(ScanFinding finding, RuleMetadata rule);
}
