package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.service.rule.RuleMetadata;

import java.util.List;

/**
 * 内置规则元数据仓储端口。
 */
public interface IBuiltinRuleRepository {

    List<RuleMetadata> listAll();

    RuleMetadata getByRuleId(String ruleId);

    int insertIfAbsent(RuleMetadata rule);

    int update(RuleMetadata rule);

    int deleteByRuleId(String ruleId);

    int deleteAll();
}
