# ADR-0013: 安全审计域——扫描引擎、三段评分与策略档位（security 域落地）

数据面请求可能携带真实风险：上下文泄露凭证（database.yml 密码随请求发往云端 LLM）、AI 生成命令带毒（curl 外发 ~/.ssh/id_rsa）、隐写攻击（零宽字符藏指令）。审计位置只有网关层能 100% 覆盖所有流量：AI IDE 各自实现且用户可绕过，厂商侧请求已出网为时已晚。经 grill 收敛（R1–R2，Q1–Q6 全部拍板），在 **security 域**（此前为空骨架）落地请求内容扫描引擎；audit 域只消费结果落 RequestLog（其 risk 字段已预留）。

**Status**: accepted

## Decision

- **域归属与接口**：扫描引擎在 security 域（纯决策），audit 域只消费结果落 `RequestLog`；本轮只设计领域服务端口（`ISecurityScanner` 编排 + `ISubScanner` 端口），**不新增 HTTP 审计端点**；不串联其他域。
- **阶段范围**：请求侧默认扫描（phase=REQUEST，请求体非流式，可整扫、可改写）；响应侧由 `scanResponse` 开关控制（默认关闭，R10 对齐参考引擎）——开启后 RESPONSE 同样过扫描引擎；流式响应仍受字节透传红线约束（ADR-0008），无法整体扫描/回溯打码。
- **引擎主流程**：输入 `ScanRequest{body, phase, settings}` → 空/未启用直接放行 → JSON 解析成功则**逐字符串值钻取扫描**、失败则**原文整体扫描** → **原文整体再查一遍**（兜字段名） → 去重（ruleId+evidence+location） → 三段评分 → 策略档位决策 → 摘要输出 `AuditResult{findings[], score, level, finalAction, blocked, blockedReason, sanitized, summary}`（blockedReason/sanitized 为 R10 新增）。
- **六子扫描器**（`ISubScanner`）：CredentialScanner/PathScanner **默认启用（可配置关闭）**；UnicodeScanner/NetworkScanner/ToolRiskScanner/TrackingScanner **默认关闭、配置启用**。规则代码内置（Q5a），开关/档位/阈值走 yml。
- **三段评分**：① 基础分取**最高** severity 值不累加（Info=5/Low=15/Medium=35/High=65/Critical=90）——危险性不随命中数量线性叠加；② 组合加分按本次扫描**类别集合**判断，同组合至多加一次、不同组合可叠加（凭证+外联+25、敏感文件+外联+25、隐写+外联+15、命令+敏感文件+20）；③ 总分 **min(100, 基础分+组合加分)** 封顶后反推等级：0→Clean、1–14→Info、15–34→Low、35–64→Medium、65–89→High、≥90→Critical（只升不降，单条 Critical 顶格 90，组合叠加封顶 100）。
- **策略档位（mode）**：六档（off/audit/warn/redact/confirm/block）全局配置，OFF/AUDIT/CONFIRM 只审计放行、WARN 中危以上警告、REDACT 高危以上脱敏、BLOCK 高危以上阻断（R10 对齐参考引擎）；档内等级→动作映射按策略矩阵内置常量；`blockCritical` 开关（默认 false）打开后 Critical 在任意档位强制 Block。动作归一三值 `Allow/Sanitize/Block` 落库（RequestLog.securityAction），Warn/Redact 仅中间态。
- **拦截口径**：Block → **403 Forbidden**，body 走协议族错误体形状（`error.code=security_blocked`、`error.message=一句话风险摘要`），复用 ProxyException（R9 拍板：原 400 改为 403，语义为安全策略拒绝）。
- **redact 语义（请求侧）**：仅"可打码"规则（如凭证值）做 Sanitize 改写后继续转发；不可打码的高危 finding 在 redact 档下降级 Warn（记录不拦）；sanitized=1 时**原始 body 仍全量**落 RequestLog。
- **发现模型与位置**：`ScanFinding{ruleId, scanner, categories[], severity, evidence, evidenceMasked, location, message}`；`evidenceMasked` 为打码证据（R10：前 8+****+后 4、≤16 原样、换行转空格），`evidence` 原文仅内部去重/定位且不参与 JSON 序列化（审计日志不泄露）；类别标签由规则元数据声明（凭证/敏感文件/隐写/命令/外联/追踪）；JSON 命中记 JSONPath（`$.messages[0].content`），原文命中记 `@raw[起..止]`；去重键 = ruleId+evidence+location。

## Considered Options

- **审计位置放 AI IDE / 厂商侧**：否决——IDE 各自实现且用户可绕过；厂商侧请求已出网，为时已晚；本地网关是唯一全流量覆盖点。
- **评分线性累加**：否决——一个 Critical（私钥外传）比十个 Medium 危险得多，危险性不随数量线性叠加；基础分取最高值 + 组合加分更符合直觉。
- **全局单一 mode（无矩阵）**：否决——mode × 等级策略矩阵更可解释、与运维预期一致，且保留 blockCritical 单独开关。
- **HTTP 审计端点（外部直接 POST 内容检查）**：本轮否决——属 trigger 串联，领域只出端口，收敛后再加。

## Consequences

- security 域新增实现：`ISecurityScanner`/`ISubScanner` 端口、六实现、评分/策略纯函数；`ScanRequest`/`ScanFinding`/`AuditResult` 等值对象。
- GLOSSARY 新增安全审计域术语（已落）。
- 六扫描器规则明细（检查什么/怎么检查）留待逐轮收敛（R3–R8），随 `.scratch/security-audit/spec.md` 演进，规则代码内置。
- 数据面挂载（filter/case 内嵌）与 audit 域落库联动属后续串联轮，不在本 ADR 范围内。

## Update（R9 引擎收尾）

- **追踪类别**：新增第六类别"追踪"（TrackingScanner 产出），**不参与任何组合加分**；追踪域名表与 NetworkScanner 外发表独立。
- **JSON 遍历边界**：只钻字符串值；深度上限 16、节点总数上限 5000，超限跳过并在 summary 标注；单值不截断。
- **扫描器注册**：Spring 收集 `List<ISubScanner>`（实现类 @Component 放 security 域 scanner 包），按 settings 过滤启用。
- **端口签名**：`ISecurityScanner.scan(ScanRequest)` 主签名 + `scan(String body)` 重载；ScanRequest/AuditResult/ScanFinding 均 domain 值对象。
- **同族去重**：同 scanner + 同证据 + 同位置多条规则只留最高严重度。
- **yml 终稿**：`security.audit.{enabled, mode, block-critical, scanners.*}`，credential/path 默认 true，其余默认 false。
- **发现持久化**：新增审计明细表 `security_audit_finding`（logId 关联 RequestLog）；类别集合逗号拼接；evidence 只落打码+哈希，原文 body 仍在 RequestLog。
- **阻断时序**：finalAction=BLOCK → 先写 RequestLog（blockedReason=summary、risk 全量）+ 发现明细 → 再抛 ProxyException（403）。

## Update（R10 对齐安全审计引擎实现 4）

- **风险等级与档位**：RiskLevel 增 Info 档（六档）；SecurityMode 增 off/confirm（六档，与 audit 同属放行）；评分 bands 增 Info 区间（1–14）。
- **评分封顶**：总分 **min(100, 最高基础分 + 组合加分)**，超出压回 100（"风险已拉满"）；等级只看分数区间，90 与 100 同为 Critical。
- **证据打码上移域层**：新增 EvidenceMasker（前 8+****+后 4、≤16 原样、换行转空格），ScannerSupport 统一构造 `ScanFinding.evidenceMasked`；`evidence` 原文标注 `@JSONField(serialize=false)` 不参与序列化——审计日志与接口输出不再出现明文凭证（持久化层 SecurityFindingPersistenceMapper 自有打码逻辑保持不变）。
- **结果模型**：AuditResult 增 `blockedReason`（BLOCK 时 = summary）与 `sanitized`（SANITIZE 生效标志），对齐 SecurityScanResult.blocked_reason/sanitized。
- **findings 上限**：MAX_FINDINGS=80 兜底，超限截断并在 summary 标注 truncated=true（与深度 16 / 节点 5000 边界并列，防审计日志被灌爆）。
- **单字段字节上限**：ScanSettings 增 `maxScanBytes`（0=不限），超长字符串值跳过扫描（性能保护）。
- **响应侧扫描**：ScanSettings 增 `scanRequest`/`scanResponse` 开关（默认 request=true、response=false，既有行为不变；开启后 RESPONSE 同样过引擎）。
- **规则补齐（对齐参考前缀启发式）**：CredentialScanner 增 sk-ant-（Anthropic）、gho_（GitHub OAuth）、bearer 前缀、`credential_named_field`（authorization:/cookie:/sessionid=/auth_token=/secret_key/access_key/database_url）；UnicodeScanner 零宽补 U+2060（WORD JOINER）、变体选择符补 U+E0100–E01EF；NetworkScanner 探测域名补 ip-api.com/ipify.org。
- **组合检测（行为链）**：ToolRiskScanner 增 `tool_shell_exfiltration`——文本同时含敏感文件/环境变量读取（cat .env / cat ~/.ssh / printenv / base64 ~/.ssh）与网络外发特征（curl / wget / http）→ 单条 Critical finding；与评分组合加分（命令+敏感文件、敏感文件+外联）互为补充。
- **配置映射**：`security.audit.{scan-request, scan-response, max-scan-bytes}` 并入 SecurityAuditProperties → ScanSettings 映射。
- **测试对齐**：评分断言由 115/140 改为封顶 100；新增证据打码/序列化不泄露、findings 上限、blockedReason、sanitized、响应侧开关、maxScanBytes、OFF/CONFIRM 与新规则用例（安全域 38/38 通过）。
