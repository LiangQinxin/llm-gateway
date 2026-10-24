# ADR-0014: 规则数据化、黑白名单与递归脱敏（security 域 P2）

把"检测什么、什么级别"（策略）从"怎么检测"（机制）中拆出：内置规则 65 条逐条入库（severity/enabled/toggle_key 表驱动，匹配逻辑仍留子扫描器代码）；自定义规则表承载用户黑白名单（pattern 子串匹配）；新增结构化 JSON 递归脱敏并在数据面挂载（扫描 → 决策 → 脱敏 → 转发）。经 grill 收敛（R1 Q1–Q4、R2 Q1–Q3 全部拍板，见 `.scratch/security-audit/p2-rules-blacklist-redact-design.md`），在 security 域落地，audit 域消费结果落 RequestLog/findings。

**Status**: accepted

## Decision

- **内置规则粒度（R1-Q1）**：`security_builtin_rules` 存 **65 条逐条**（rule_id 保持代码 id，如 `credential_apikey_openai`），不做文章式 25 族归一化——用户可单条开关/调级，管理端与代码同一套命名；`pattern`/`action` 遗留列置空不用（匹配逻辑在子扫描器代码，处置由策略矩阵决定）。
- **既有表处置（R1-Q2）**：对已提交 schema 的两张表 **ALTER 收敛语义**（改列注释/枚举），不重建；`security_custom_rules.rule_type` 语义收敛为 `blacklist|whitelist`、`category` 为 `domain|tool|path|keyword`；schema 唯一来源 `docs/mysql/sql/llm_gateway.sql` 同步更新。
- **种子机制**：启动幂等播种（`INSERT ... ON DUPLICATE KEY UPDATE rule_id=rule_id`，已存在跳过不覆盖用户修改、新 rule_id 补插）；`security.scan_tracking` 为新增 toggle_key 映射（对应 `ScanSettings.isTracking()`）；全局开关四键映射：scan_unicode/scan_network/scan_tools/scan_tracking。
- **规则缓存**：启动全量加载 `ConcurrentHashMap`（规则 <100），管理接口写操作后 `refresh()` 立即生效；表读取失败 fail-open 降级代码默认（记 error，不阻断）。
- **元数据驱动（引擎出口）**：去重后、评分前，按 ruleId 直查缓存：未入库保留代码默认、`enabled=0` 或全局开关关闭则丢弃、`severity` 表覆盖；**动态规则（credential_password/token/secret 值启发式）severity 只升不降**（R2-Q3：`max(表值, 代码启发式值)`），静态规则双向覆盖。
- **黑白名单（R1-Q3）**：白名单按类别映射豁免（domain→network、path→file、keyword→credential、tool→tool），不放宽到整段全豁免；黑名单命中产生 `category="custom"` finding（categories 空、不参与组合加分），**severity 并入统一评分矩阵**出动作（action 列仅管理提示，不绕过 blockCritical）。
- **递归脱敏**：新增 `RecursiveRedactor`（键名级 isSecretField 整值打码 + 字符串级按词切词命中前缀只涂该词 + PEM 整块替换；maskString 前4+****+后4、≤8 抹空；非法 JSON 降级原文正则兜底）；`RedactPatterns` 与 CredentialScanner 共享密钥前缀表（DRY，防"扫到但脱不掉"）。
- **数据面挂载（R1-Q4，仅请求面 R2-Q2）**：新增 `SecurityAuditCase`（api 根包 `ISecurityAuditCase`）编排——扫描 → BLOCK 抛 `ProxyException(403, security_blocked)` → SANITIZE 递归脱敏改写 body → 转发；挂载点 `ProxyCase.prepare()`。**响应面检测不做**（流式透传红线 ADR-0008）。
- **审计留痕（R2-Q1）**：**仅风险路径**——BLOCK/SANITIZE 请求落 `RequestLog`（risk 六字段、blockedReason、requestBody=原始 body、sanitized 标志）+ `security_audit_finding` 明细（logId 关联、evidence 只存打码+哈希，复用 SecurityFindingPersistenceMapper）；clean 请求不落（数据面完整 RequestLog 属既有审计域联动轮）。
- **管理接口**：`/admin/api/security/rules/**` 8 个接口（内置改/重置、自定义 CRUD/启停），链路 trigger → `ISecurityRuleCase` → Repository → 表；写操作后缓存刷新。

## Considered Options

- **25 族粗粒度（对齐文章）**：否决（R1-Q1）——管理端简洁但无法单条开关；65 条逐条粒度真实可控，且免去归一化映射表。
- **重建两表（DROP + CREATE）**：否决（R1-Q2）——两表已随引擎提交进 schema，ALTER 收敛语义改动面最小且保留审计连续。
- **黑名单 action 直判**：否决（R1-Q3）——绕过评分与 blockCritical 兜底，决策路径不统一。
- **数据面挂载留待后续轮**：否决（R1-Q4）——用户明确本轮连数据面一起做，形成"规则→扫描→处置→留痕"完整闭环。
- **全量留痕 / 响应面挂载 / 表值双向覆盖**：否决（R2）——分别因范围重叠、流式透传红线、压制启发式升级。

## Consequences

- security 域新增：`RuleMetadata`/`CustomRuleMeta`/`SecurityRuleCache`/`CustomRuleMatcher`/`RecursiveRedactor`/`RedactPatterns`；infrastructure 新增两表 Mapper + SecurityFindingMapper + 三个 Repository + RuleSeedService；api 新增 `ISecurityRuleCase`/`ISecurityAuditCase`；cases 新增 `SecurityRuleCase`/`SecurityAuditCase`；trigger 新增 `SecurityRuleController`。
- 既有最小改动：`SecurityScannerImpl` 3 处（元数据驱动出口、白名单前置、黑名单追加）、`ProxyCase.prepare` 挂载审计、`application-dev.yml` 追加 mapper-locations。
- 不动的：6 个子扫描器规则正则、SecurityRiskScorer、SecurityPolicy、AuditResult 模型、审计表结构、`security_findings` 遗留表。
- 测试：18 条（app/src/test 按被测包路径，@SpringBootTest 真容器，入参出参 log+JSON）。

## Refinement Notes（二轮自查补强）

- **seed 默认值与类别开关联动**：seed 全部 `enabled=1`，但 ScanSettings 默认 unicode/network/toolRisk/tracking=false——规则生效 = 规则 enabled **且**类别开关开；管理端需展示 toggle_key 生效状态。
- **双层白名单**：自定义白名单**追加**到代码内置白名单（如 NetworkScanner.BENIGN_DOMAINS）之上，非替换；白名单 pattern 建议长且上下文明确的子串，避免 `token`/`api` 等短词导致整类豁免。
- **留痕事务边界**：RequestLog + findings 同一 `@Transactional` 一次提交，失败 catch 记 warn、不阻断/不改变策略结果；BLOCK 请求在认证前（apiKeyId 为空属预期）。
- **custom finding 不被误杀**：黑名单 finding（ruleId=`custom.*`）在出口按"未入库规则保留"处理。
- **性能**：普通请求 2 次 JSON 解析（scan + prepare），SANITIZE 另加 1 次序列化；不改 ISecurityScanner 接口。
- **severity 逐条核对**：种子清单 severity 实现时逐条从子扫描器代码核对。
- **测试前置**：两表 ALTER 先落 llm_gateway.sql 并执行到本地库；seed-on-start 保证种子就绪。
- **改动① 位置（三轮）**：元数据过滤放在**收集循环后、MAX_FINDINGS=80 截断前**——禁用规则不占截断预算、severity 覆盖在 dedup/评分前统一；`custom.*` finding 走"未入库保留"不误杀。
- **处理顺序（三轮）**：审计（scan 原始 body + 可能脱敏）在 `MessageSanitizer` 之前；Settings 直接注入既有 `securityScanSettings` Bean，默认 mode=warn、credential/path=true、其余类别 false。
- **证据与边界（三轮）**：黑名单 finding 落库 evidence=命中 text 片段（截断+打码+哈希），evidenceMasked 仅展示；isSecretField 大小写不敏感；数字/布尔/null 不脱敏（避免类型破坏）；PUT builtin 的 rule_id/toggle_key 只读；缓存 refresh 全量重载有一次旧缓存窗口（可接受）。
