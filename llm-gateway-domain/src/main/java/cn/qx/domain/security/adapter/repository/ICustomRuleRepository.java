package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.service.rule.CustomRuleMeta;

import java.util.List;

/**
 * 自定义黑白名单仓储端口。
 */
public interface ICustomRuleRepository {

    /** 列出全部自定义黑白名单规则。 */
    List<CustomRuleMeta> listAll();

    /** 按规则类型（blacklist/whitelist）列出自定义规则。 */
    List<CustomRuleMeta> listByType(String ruleType);

    /** 按主键 ID 查询自定义规则，不存在返回 null。 */
    CustomRuleMeta getById(String id);

    /** 统计自定义规则总数。 */
    long count();

    /** 新增自定义黑白名单规则，返回影响行数。 */
    int insert(CustomRuleMeta rule);

    /** 更新自定义规则，返回影响行数。 */
    int update(CustomRuleMeta rule);

    /** 按主键 ID 删除自定义规则，返回影响行数。 */
    int deleteById(String id);
}
