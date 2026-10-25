# ADR-0016: 规则级动作覆盖全局 mode 与自定义规则脱敏

ADR-0014 把 `custom_rule.action` / `builtin_rule.action` 列为"管理提示动作，实际处置由统一评分矩阵决定"。联调发现：用户在前端选了 redact/block，请求仍原样转发上游——这两列被运行时完全忽略。本 ADR 把它们升格为真正的 per-rule 动作，并让脱敏器识别自定义 keyword pattern。

经 grill 收敛（R1 Q1–Q4、R2 Q5–Q8、R3 Q9–Q12 全部拍板）。

**Status**: accepted

## Decision

- **规则级 action 完全覆盖全局 mode**（Q1A/Q5A）：规则上显式选了 warn/redact/block，就按规则走；全局 `mode` 与 `block-critical` 只对"action 留空"的规则生效。规则级 action 优先级最高，blockCritical 不覆盖它。
- **action 留空 = 跟随全局 mode**（Q6A）：前端"命中动作"下拉允许空值；空时走原 severity × mode 矩阵。内置规则出厂 action 一律 null（Q12A），不预设。
- **redactable 由规则 action 动态决定**（Q2A/Q9A）：`action=redact → finding.redactable=true`，其他 action → false。scanner 不再硬编码 redactable，由 `addIfEnabled` 根据规则元数据覆盖。
- **globalAction 取 finding 动作最严值**（Q10A）：任何 finding=BLOCK → 全局 BLOCK；否则任何 finding=REDACT → 全局 SANITIZE；否则 ALLOW。不再 RiskLevel 反推。
- **自定义 pattern 脱敏形态**（Q3A/Q7C）：命中段 < 12 字符整体替换为 `[REDACTED]`；≥ 12 字符走 `maskString()`（前 4 + `****` + 后 4）。大小写不敏感匹配，替换原文。
- **脱敏器注入规则缓存**（Q4A）：`RecursiveRedactorServiceImpl` 注入 `ISecurityRuleCacheService`，从 snapshot 读所有 enabled blacklist pattern，与扫描器共享同一份规则数据。
- **内置规则 action 一起启用**（Q8B）：`builtin_rule.action` 从遗留列升格为生效列；`RuleMetadata` 加 `action` 字段。
- **缓存写后刷新**（Q11A）：RuleController 的 create/update/delete 后调 `ruleCache.refresh()`，改完立即生效。

## Considered Options

- **规则 action 仅记录（现状）**：否决——用户显式选了 block 却放行，前端等于骗人。
- **blockCritical 覆盖规则 action（Q5B/C）**：否决——会让 per-rule action 形同虚设。
- **每条规则必填 action（Q6B/C）**：否决——用户加一堆规则时被迫重复选择，全局 mode 失去意义。
- **RiskLevel 矩阵保留 + 规则覆盖（Q10B/C）**：否决——跟"规则 action 完全覆盖"语义冲突。
- **所有自定义子串一刀切 `[REDACTED]`（Q3B）**：否决——长句子里多个命中全是 `[REDACTED]`，可读性差。
- **本次只做 custom_rule、builtin_rule.action 留作遗留列（Q8A）**：否决——两张表结构对称，只改一半留下不一致。

## Consequences

- **领域模型**：`RuleMetadata` 加 `action` 字段；`CustomRuleMeta.action` 注释语义更新；`FindingAction` 枚举不动。
- **策略层**：`SecurityScannerImpl.applyActions()` 先查规则 action，非空直接用；空才走 `SecurityPolicy.actionFor`。`SecurityPolicy.globalAction()` 改为 finding 动作取最严。
- **自定义匹配器**：`CustomRuleMatcherServiceImpl.customFindings()` 读 `rule.getAction()` 写入 finding；redactable 不再硬编码 false。
- **脱敏器**：`RecursiveRedactorServiceImpl` 注入 `ISecurityRuleCacheService`，除 `SECRET_TOKENS` 外追加自定义 pattern 替换（短词整体替换、长词 maskString）。
- **Controller**：`SecurityRuleController` 写操作后 `refresh()`。
- **不变**：数据库 schema（两表 action 列已存在）、子扫描器正则、`SecurityRiskScorer`、`AuditResult` 结构、白名单逻辑。
- **测试**：app/src/test 新增 per-rule action 矩阵测试（block/redact/warn/空 × custom/builtin）、自定义 pattern 脱敏形态测试、缓存刷新生效测试。
