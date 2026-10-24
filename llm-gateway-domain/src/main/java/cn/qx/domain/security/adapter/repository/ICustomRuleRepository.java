package cn.qx.domain.security.adapter.repository;

import cn.qx.domain.security.service.rule.CustomRuleMeta;

import java.util.List;

/**
 * 自定义黑白名单仓储端口。
 */
public interface ICustomRuleRepository {

    List<CustomRuleMeta> listAll();

    List<CustomRuleMeta> listByType(String ruleType);

    CustomRuleMeta getById(String id);

    long count();

    int insert(CustomRuleMeta rule);

    int update(CustomRuleMeta rule);

    int deleteById(String id);
}
