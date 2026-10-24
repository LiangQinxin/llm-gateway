package cn.qx.infrastructure.dao;

import cn.qx.infrastructure.dao.po.SecurityBuiltinRulePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * security_builtin_rules 表 Mapper。
 */
@Mapper
public interface SecurityBuiltinRuleMapper {

    List<SecurityBuiltinRulePO> listAll();

    SecurityBuiltinRulePO queryByRuleId(@Param("ruleId") String ruleId);

    int insertIfAbsent(SecurityBuiltinRulePO rule);

    int updateByRuleId(SecurityBuiltinRulePO rule);

    int deleteByRuleId(@Param("ruleId") String ruleId);

    int deleteAll();
}
