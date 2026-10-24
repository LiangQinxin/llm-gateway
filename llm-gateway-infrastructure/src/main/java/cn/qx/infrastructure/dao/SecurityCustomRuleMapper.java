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

    /** 查询全部自定义规则，按创建时间与 id 升序。 */
    List<SecurityCustomRulePO> listAll();

    /** 按规则类型（rule_type）过滤查询，按创建时间与 id 升序。 */
    List<SecurityCustomRulePO> listByType(@Param("ruleType") String ruleType);

    /** 按主键 id 查询自定义规则。 */
    SecurityCustomRulePO queryById(@Param("id") String id);

    /** 统计自定义规则总数。 */
    long count();

    /** 新增一条自定义规则。 */
    int insert(SecurityCustomRulePO rule);

    /** 按主键更新自定义规则全字段，返回影响行数。 */
    int update(SecurityCustomRulePO rule);

    /** 按主键删除自定义规则，返回影响行数。 */
    int deleteById(@Param("id") String id);
}
