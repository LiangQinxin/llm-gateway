package cn.qx.cases.security;

import cn.qx.api.ISecurityRuleCase;
import cn.qx.api.dto.security.BuiltinRuleResult;
import cn.qx.api.dto.security.BuiltinRuleUpdateRequest;
import cn.qx.api.dto.security.CustomRuleRequest;
import cn.qx.api.dto.security.CustomRuleResult;
import cn.qx.api.dto.security.CustomRuleUpdateRequest;
import cn.qx.domain.security.adapter.repository.IBuiltinRuleRepository;
import cn.qx.domain.security.adapter.repository.ICustomRuleRepository;
import cn.qx.domain.security.model.valobj.SecuritySeverity;
import cn.qx.domain.security.service.rule.CustomRuleMeta;
import cn.qx.domain.security.service.rule.RuleMetadata;
import cn.qx.domain.security.service.rule.ISecurityRuleCacheService;
import cn.qx.domain.security.service.rule.SecurityRuleCatalog;
import cn.qx.types.common.TimeUtils;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

/**
 * 安全规则管理编排；任何写操作后刷新进程内缓存。
 */
@Slf4j
@Service
public class SecurityRuleCase implements ISecurityRuleCase {

    private static final int MAX_CUSTOM_RULES = 200;
    private static final Set<String> RULE_TYPES = Set.of("blacklist", "whitelist");
    /** 自定义规则允许类别集合（domain/tool/path/keyword） */
    private static final Set<String> CUSTOM_CATEGORIES =
            Set.of("domain", "tool", "path", "keyword");
    private static final Set<String> CUSTOM_ACTIONS = Set.of("warn", "redact", "block");

    private final Object customRuleCreateLock = new Object();

    /** 内置规则仓储端口 */
    @Resource
    private IBuiltinRuleRepository builtinRuleRepository;

    /** 自定义规则仓储端口 */
    @Resource
    private ICustomRuleRepository customRuleRepository;

    /** 安全规则进程内缓存服务 */
    @Resource
    private ISecurityRuleCacheService ruleCache;

    /** 编程式事务管理器（重置/创建落库用） */
    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public List<BuiltinRuleResult> listBuiltin() {
        return ruleCache.listBuiltin().stream().map(BuiltinRuleResult::from).toList();
    }

    @Override
    public BuiltinRuleResult getBuiltin(String ruleId) {
        RuleMetadata rule = ruleCache.findBuiltin(ruleId);
        if (rule == null) {
            throw new AppException(ResponseCode.NOT_FOUND.getCode(), "安全内置规则不存在: " + ruleId);
        }
        return BuiltinRuleResult.from(rule);
    }

    @Override
    public BuiltinRuleResult updateBuiltin(String ruleId, BuiltinRuleUpdateRequest request) {
        if (isBlank(ruleId) || request == null) {
            throw illegal("内置规则 ruleId 不能为空");
        }
        RuleMetadata current = builtinRuleRepository.getByRuleId(ruleId);
        if (current == null) {
            throw new AppException(ResponseCode.NOT_FOUND.getCode(),
                    "安全内置规则不存在: " + ruleId);
        }
        current.setSeverity(parseSeverity(request.getSeverity(), current.getSeverity()));
        current.setEnabled(request.getEnabled() == null
                ? current.isEnabled() : request.getEnabled());
        current.setTitle(defaultIfBlank(request.getTitle(), current.getTitle()));
        current.setDescription(defaultIfBlank(
                request.getDescription(), current.getDescription()));
        current.setUpdatedAt(TimeUtils.now());
        builtinRuleRepository.update(current);
        ruleCache.refresh();
        log.info("安全内置规则已更新: {}", JSON.toJSONString(current));
        return getBuiltin(current.getRuleId());
    }

    @Override
    public void resetBuiltin() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            builtinRuleRepository.deleteAll();
            String now = TimeUtils.now();
            for (RuleMetadata rule : SecurityRuleCatalog.defaults()) {
                rule.setCreatedAt(now);
                rule.setUpdatedAt(now);
                builtinRuleRepository.insertIfAbsent(rule);
            }
        });
        ruleCache.refresh();
        log.info("安全内置规则已恢复出厂设置");
    }

    @Override
    public List<CustomRuleResult> listCustom(String ruleType) {
        return (isBlank(ruleType)
                ? customRuleRepository.listAll()
                : customRuleRepository.listByType(ruleType))
                .stream().map(CustomRuleResult::from).toList();
    }

    @Override
    public CustomRuleResult createCustom(CustomRuleRequest request) {
        if (request == null) {
            throw illegal("自定义规则不能为空");
        }
        CustomRuleMeta rule = new CustomRuleMeta();
        rule.setRuleType(request.getRuleType());
        rule.setCategory(request.getCategory());
        rule.setName(request.getName());
        rule.setPattern(request.getPattern());
        rule.setSeverity(request.getSeverity() == null
                ? null : parseSeverity(request.getSeverity(), null));
        rule.setAction(defaultIfBlank(request.getAction(), "warn"));
        rule.setEnabled(request.getEnabled() == null || request.getEnabled());
        rule.setDescription(request.getDescription());
        validateCustom(rule);
        String now = TimeUtils.now();
        rule.setId(isBlank(rule.getId()) ? TimeUtils.uuid() : rule.getId());
        rule.setRuleType(rule.getRuleType().toLowerCase());
        rule.setCategory(rule.getCategory().toLowerCase());
        rule.setName(defaultIfBlank(rule.getName(), rule.getPattern()));
        rule.setDescription(defaultIfBlank(rule.getDescription(), ""));
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        synchronized (customRuleCreateLock) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                if (customRuleRepository.count() >= MAX_CUSTOM_RULES) {
                    throw illegal("自定义规则数量不能超过 " + MAX_CUSTOM_RULES);
                }
                customRuleRepository.insert(rule);
            });
        }
        ruleCache.refresh();
        log.info("自定义安全规则已创建: {}", JSON.toJSONString(rule));
        return CustomRuleResult.from(rule);
    }

    @Override
    public CustomRuleResult updateCustom(String id, CustomRuleUpdateRequest request) {
        if (isBlank(id) || request == null) {
            throw illegal("自定义规则 ID 不能为空");
        }
        CustomRuleMeta current = requireCustom(id);
        if (request.getPattern() != null) {
            current.setPattern(request.getPattern());
        }
        if (request.getSeverity() != null) {
            current.setSeverity(parseSeverity(request.getSeverity(), current.getSeverity()));
        }
        if (request.getAction() != null) {
            current.setAction(request.getAction());
        }
        if (request.getDescription() != null) {
            current.setDescription(request.getDescription());
        }
        validateCustom(current);
        current.setUpdatedAt(TimeUtils.now());
        customRuleRepository.update(current);
        ruleCache.refresh();
        log.info("自定义安全规则已更新: {}", JSON.toJSONString(current));
        return CustomRuleResult.from(current);
    }

    @Override
    public CustomRuleResult toggleCustom(String id, boolean enabled) {
        CustomRuleMeta current = requireCustom(id);
        current.setEnabled(enabled);
        current.setUpdatedAt(TimeUtils.now());
        customRuleRepository.update(current);
        ruleCache.refresh();
        return CustomRuleResult.from(current);
    }

    @Override
    public void deleteCustom(String id) {
        customRuleRepository.deleteById(id);
        ruleCache.refresh();
        log.info("自定义安全规则已删除: id={}", id);
    }

    private CustomRuleMeta requireCustom(String id) {
        CustomRuleMeta rule = customRuleRepository.getById(id);
        if (rule == null) {
            throw new AppException(ResponseCode.NOT_FOUND.getCode(), "自定义规则不存在: " + id);
        }
        return rule;
    }

    private static void validateCustom(CustomRuleMeta rule) {
        if (rule.getRuleType() == null
                || !RULE_TYPES.contains(rule.getRuleType().toLowerCase())) {
            throw illegal("ruleType 只能是 blacklist 或 whitelist");
        }
        if (rule.getCategory() == null
                || !CUSTOM_CATEGORIES.contains(rule.getCategory().toLowerCase())) {
            throw illegal("category 只能是 domain/tool/path/keyword");
        }
        if (isBlank(rule.getPattern())) {
            throw illegal("pattern 不能为空");
        }
        if (rule.getSeverity() == null) {
            throw illegal("severity 不能为空");
        }
        if (rule.getAction() == null
                || !CUSTOM_ACTIONS.contains(rule.getAction().toLowerCase())) {
            throw illegal("action 只能是 warn/redact/block");
        }
    }

    private static SecuritySeverity requireSeverity(SecuritySeverity severity) {
        if (severity == null) {
            throw illegal("severity 不能为空");
        }
        return severity;
    }

    private static SecuritySeverity parseSeverity(String value,
                                                  SecuritySeverity fallback) {
        if (isBlank(value)) {
            return requireSeverity(fallback);
        }
        try {
            return SecuritySeverity.parse(value);
        } catch (IllegalArgumentException e) {
            throw illegal("非法 severity: " + value);
        }
    }

    private static AppException illegal(String message) {
        return new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), message);
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
