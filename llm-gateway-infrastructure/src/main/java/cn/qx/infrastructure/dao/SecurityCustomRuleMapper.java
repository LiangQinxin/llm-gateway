package cn.qx.infrastructure.dao;

import cn.qx.infrastructure.dao.po.SecurityCustomRulePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * security_custom_rules 表 Mapper。
 */
@Mapper
public interface SecurityCustomRuleMapper {

    List<SecurityCustomRulePO> listAll();

    List<SecurityCustomRulePO> listByType(@Param("ruleType") String ruleType);

    SecurityCustomRulePO queryById(@Param("id") String id);

    long count();

    int insert(SecurityCustomRulePO rule);

    int update(SecurityCustomRulePO rule);

    int deleteById(@Param("id") String id);
}
