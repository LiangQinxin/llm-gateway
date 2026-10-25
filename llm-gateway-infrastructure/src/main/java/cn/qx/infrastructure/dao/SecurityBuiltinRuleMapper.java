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

    /** 查询全部内置规则，按主键 id 升序。 */
    List<SecurityBuiltinRulePO> listAll();

    /** 按 rule_id 查询内置规则。 */
    SecurityBuiltinRulePO queryByRuleId(@Param("ruleId") String ruleId);

    /** 幂等插入：rule_id 已存在时不更新任何字段（ON DUPLICATE KEY UPDATE 自赋），返回新增行数。 */
    int insertIfAbsent(SecurityBuiltinRulePO rule);

    /** 按 rule_id 更新 severity/action/title/description/enabled/updated_at（不动 category/pattern），返回影响行数。 */
    int updateByRuleId(SecurityBuiltinRulePO rule);

    /** 按 rule_id 删除内置规则，返回影响行数。 */
    int deleteByRuleId(@Param("ruleId") String ruleId);

    /** 清空全部内置规则，返回影响行数。 */
    int deleteAll();
}
