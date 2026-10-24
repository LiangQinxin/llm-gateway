package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.adapter.repository.ICustomRuleRepository;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.CustomRuleMeta;
import cn.qx.infrastructure.dao.SecurityCustomRuleMapper;
import cn.qx.infrastructure.dao.po.SecurityCustomRulePO;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 自定义黑白名单仓储适配器。
 */
@Repository
public class SecurityCustomRuleRepository implements ICustomRuleRepository {

    /** security_custom_rules 表 Mapper。 */
    @Resource
    private SecurityCustomRuleMapper mapper;

    @Override
    public List<CustomRuleMeta> listAll() {
        return mapper.listAll().stream().map(SecurityCustomRuleRepository::toEntity).toList();
    }

    @Override
    public List<CustomRuleMeta> listByType(String ruleType) {
        return mapper.listByType(ruleType).stream()
                .map(SecurityCustomRuleRepository::toEntity)
                .toList();
    }

    @Override
    public CustomRuleMeta getById(String id) {
        return toEntity(mapper.queryById(id));
    }

    @Override
    public long count() {
        return mapper.count();
    }

    @Override
    public int insert(CustomRuleMeta rule) {
        return mapper.insert(toPO(rule));
    }

    @Override
    public int update(CustomRuleMeta rule) {
        return mapper.update(toPO(rule));
    }

    @Override
    public int deleteById(String id) {
        return mapper.deleteById(id);
    }

    private static SecurityCustomRulePO toPO(CustomRuleMeta entity) {
        SecurityCustomRulePO po = new SecurityCustomRulePO();
        BeanUtils.copyProperties(entity, po);
        po.setSeverity(entity.getSeverity() == null ? null : entity.getSeverity().code());
        po.setEnabled(entity.isEnabled() ? 1 : 0);
        return po;
    }

    private static CustomRuleMeta toEntity(SecurityCustomRulePO po) {
        if (po == null) {
            return null;
        }
        CustomRuleMeta entity = new CustomRuleMeta();
        BeanUtils.copyProperties(po, entity);
        entity.setSeverity(SecuritySeverity.parse(po.getSeverity()));
        entity.setEnabled(Integer.valueOf(1).equals(po.getEnabled()));
        return entity;
    }
}
