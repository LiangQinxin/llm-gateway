package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.security.adapter.repository.IBuiltinRuleRepository;
import cn.qx.domain.security.service.rule.RuleMetadata;
import cn.qx.domain.security.service.rule.ISecurityRuleCacheService;
import cn.qx.domain.security.service.rule.SecurityRuleCatalog;
import cn.qx.types.common.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时幂等播种 65 条内置规则；已存在记录不覆盖用户修改。
 */
@Slf4j
@Component
@Order(0)
public class RuleSeedService implements ApplicationRunner {

    private final IBuiltinRuleRepository repository;
    private final ISecurityRuleCacheService ruleCache;
    private final boolean seedOnStart;

    public RuleSeedService(IBuiltinRuleRepository repository,
                           ISecurityRuleCacheService ruleCache,
                           @Value("${security.rules.seed-on-start:true}") boolean seedOnStart) {
        this.repository = repository;
        this.ruleCache = ruleCache;
        this.seedOnStart = seedOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seedOnStart) {
            try {
                seed();
            } catch (RuntimeException e) {
                log.error("安全内置规则播种失败，按 fail-open 继续启动: {}", e.getMessage());
            }
        }
        ruleCache.refresh();
    }

    /**
     * 幂等补插；已有 rule_id 的 enabled/severity/文案保持不变。
     */
    public int seed() {
        String now = TimeUtils.now();
        int inserted = 0;
        for (RuleMetadata rule : SecurityRuleCatalog.defaults()) {
            rule.setCreatedAt(now);
            rule.setUpdatedAt(now);
            inserted += repository.insertIfAbsent(rule);
        }
        log.info("安全内置规则幂等播种完成: processed={}, changed={}",
                SecurityRuleCatalog.defaults().size(), inserted);
        return inserted;
    }
}
