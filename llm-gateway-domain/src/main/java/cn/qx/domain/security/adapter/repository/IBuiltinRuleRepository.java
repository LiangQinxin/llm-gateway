package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.service.rule.RuleMetadata;

import java.util.List;

/**
 * 内置规则元数据仓储端口。
 */
public interface IBuiltinRuleRepository {

    /** 列出全部内置规则元数据。 */
    List<RuleMetadata> listAll();

    /** 按规则 ID 查询单条内置规则元数据，不存在返回 null。 */
    RuleMetadata getByRuleId(String ruleId);

    /** 内置规则不存在时播种插入，返回影响行数。 */
    int insertIfAbsent(RuleMetadata rule);

    /** 更新内置规则元数据，返回影响行数。 */
    int update(RuleMetadata rule);

    /** 按规则 ID 删除内置规则，返回影响行数。 */
    int deleteByRuleId(String ruleId);

    /** 清空全部内置规则，返回影响行数。 */
    int deleteAll();
}
