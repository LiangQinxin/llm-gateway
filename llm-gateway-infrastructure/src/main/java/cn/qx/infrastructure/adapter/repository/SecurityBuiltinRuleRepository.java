package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.adapter.repository.IBuiltinRuleRepository;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.RuleMetadata;
import cn.qx.infrastructure.dao.SecurityBuiltinRuleMapper;
import cn.qx.infrastructure.dao.po.SecurityBuiltinRulePO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 内置规则元数据仓储适配器。
 */
@Repository
public class SecurityBuiltinRuleRepository implements IBuiltinRuleRepository {

    @Resource
    private SecurityBuiltinRuleMapper mapper;

    @Override
    public List<RuleMetadata> listAll() {
        return mapper.listAll().stream().map(SecurityBuiltinRuleRepository::toEntity).toList();
    }

    @Override
    public RuleMetadata getByRuleId(String ruleId) {
        return toEntity(mapper.queryByRuleId(ruleId));
    }

    @Override
    public int insertIfAbsent(RuleMetadata rule) {
        return mapper.insertIfAbsent(toPO(rule));
    }

    @Override
    public int update(RuleMetadata rule) {
        return mapper.updateByRuleId(toPO(rule));
    }

    @Override
    public int deleteByRuleId(String ruleId) {
        return mapper.deleteByRuleId(ruleId);
    }

    @Override
    public int deleteAll() {
        return mapper.deleteAll();
    }

    private static SecurityBuiltinRulePO toPO(RuleMetadata entity) {
        SecurityBuiltinRulePO po = new SecurityBuiltinRulePO();
        BeanUtils.copyProperties(entity, po);
        po.setSeverity(entity.getSeverity() == null ? null : entity.getSeverity().code());
        po.setEnabled(entity.isEnabled() ? 1 : 0);
        return po;
    }

    private static RuleMetadata toEntity(SecurityBuiltinRulePO po) {
        if (po == null) {
            return null;
        }
        RuleMetadata entity = new RuleMetadata();
        BeanUtils.copyProperties(po, entity);
        entity.setSeverity(parseSeverity(po.getSeverity()));
        entity.setEnabled(Integer.valueOf(1).equals(po.getEnabled()));
        return entity;
    }

    private static SecuritySeverity parseSeverity(String value) {
        return SecuritySeverity.parse(value);
    }
}
