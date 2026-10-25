# ADR-0015: ProxyCase 七步管道与全量数据面审计留痕（单写者 + 脱敏贯穿 + 流式 exactly-once）

把 ProxyCase 编排收敛为固定七步管道（解析模型 → 安全检查 → 找渠道 → 排队 → 循环转发 → 记日志 → 扣配额），并把数据面留痕从"仅 BLOCK/SANITIZE 风险路径"升级为**全量**（每请求一条 RequestLog，唯一例外=全库零启用渠道 503）。设计收敛为四条审计原则并适配 Java/MySQL 架构：请求侧一次读入+脱敏贯穿、响应统一同构 response_choices、流式落账 exactly-once 三分支、统一落库单写者漏斗。经 grill 收敛（R1 Q1–Q9 全部拍板，见 `.scratch/proxy-orchestration/spec.md`）。

**Status**: accepted

## Decision

- **七步管道（ProxyCase 只串联不实现）**：① 解析模型（ProxyCase.prepare，协议族 ADR-0010/0011）→ ② 安全检查（security 域 ISecurityAuditCase：Allow/Sanitize/Block，Block 先留痕后抛 403 security_blocked，ADR-0013 R9 时序不变）→ ③ 找渠道（channel 域 listEnabledOrdered）→ ④ 排队（ChannelSelector，ADR-0012）→ ⑤ 循环转发（IDispatchInvoker：失败分类切换、per-attempt 烘焙、成功返回 DispatchOutcome）→ ⑥ 记日志（audit 域单写者）→ ⑦ 扣配额（identity 域，仅 2xx）。鉴权/配额预检（401/403）在管道之前，失败同样记日志。
- **状态码矩阵（Q2/Q3/Q5 拍板）**：成功=上游原样；阻断=403 security_blocked（**不改 451**）；全部失败·HTTP=透传最后一个被尝试渠道状态码；全部失败·网络=502 upstream_unavailable；无渠道匹配=404 model_not_found；**全库零启用渠道=503 no_available_channel（唯一不记日志）**；认证/请求错误=401/403/400（400 的 model 用 `unknown` 占位，不动 `IRequestLogCase.record` 校验）；流式客户端断开=499 client_cancelled；流式首帧前失败=502。`DispatchOutcome` 增加 reason 区分（MODEL_NOT_FOUND/NO_AVAILABLE_CHANNEL），不新增态。
- **失败分类保持（Q3）**：网络/5xx/429/401/403 切换下一渠道，其余 4xx 直接透传（ADR-0012 §4 不变）。
- **重试上限（Q4）**：新增 `dispatch.retry-count`（yml，默认 null=不设上限、沿用排空队列）；有值时最大尝试次数 = min(retry+1, 队列长度)；每渠道最多一次、无同渠道重试（ADR-0012 §7 不变）；`RequestLog.is_retry` = 尝试渠道数>1。
- **安全检查与脱敏贯穿（Q1/Q6）**：安全检查以**原始请求体**为输入（gate-before-parse，非法 JSON 原文整体扫描保留）；产出脱敏后转发体；**落库请求体=脱敏副本**（findings 非空时对转发体再过 RecursiveRedactor，与策略动作解耦）——**P2 的"风险路径落原始 body"升级为此语义**，证据由 findings 的 evidenceMasked+哈希承担，敏感信息不进库；响应侧**不扫描**（ADR-0013 scanResponse 默认 false 保持），但响应情况记入日志。
- **响应留存**：统一同构 response_choices——非流式 2xx 从 raw 提取 choices；流式字节原样透传（ADR-0008 红线）+ 旁路 tee 累积 usage/content/reasoning/tool_calls（≤4MB 截断+标记）、终止时重建；快照降频（每帧只同步 usage，content 终止重建，无 16KB 增量）；**流式 exactly-once 三分支**绑定 ResponseBodyEmitter：正常完成 200 / 客户端断开 499+快照（不丢账）/ 首帧前失败 502。
- **单写者记日志（Q7/Q9-a，统一落库漏斗）**：新增 audit 域 `IDataPlaneAuditCase.recordDataPlane(DataPlaneLogData, findings)`——RequestLog + security_audit_finding 同一事务提交；`SecurityAuditCase` 降为纯决策（返回 `SecurityAuditOutcome{sanitizedBody, audit, blocked}`，删除留痕依赖）；ProxyCase 为唯一触发点（BLOCK 在步骤②、其余在步骤⑥）。**静默化**：recordDataPlane 失败 catch+warn，不阻断主流程、不改变策略结果、不影响配额。trace_id 入口预生成贯穿日志与 findings。
- **日志保留档（策略档位）**：`request-log.policy`（detailed/brief/basic，默认 detailed）：basic=正文不落库；brief=requestBody 只留 messages 最近 3 条；detailed=原样；裁剪失败回退原样。保留期清理沿用既有 deleteBefore/clearAll，自动清理另行立项。
- **配额（Q8，ADR-0010 不变）**：仅 2xx 扣 totalTokens（非流式 usage / 流式 SSE usage 帧）；拿不到不记、不估算（**不采用本地估算**）、不阻断；顺序=记日志（⑥）→扣配额（⑦）。

## Considered Options

- **阻断用 451（用户初稿）**：否决（Q2）——RFC 语义是"因法律原因不可用"，与安全策略拒绝不符；改 451 需动 ADR-0013 R9 与全部测试。保持 403 security_blocked。
- **全部失败统一 502 / 删失败分类表**：否决（Q3）——透传最后一个被尝试渠道的 HTTP 错误可真实排障；请求本身错误的 4xx 切换必重蹈覆辙。保持 ADR-0012 分类表 + 透传语义。
- **retry-count=0 时只试首渠道 / 默认设小值**：否决（Q4）——破坏既有 failover 语义；默认 null=不设上限，行为零变化。
- **日志双写者（SecurityAuditCase 写风险路径 + ProxyCase 写其余）**：否决（Q9-a）——SANITIZE 请求会出两条日志，违反"每请求一条"；字段不统一（风险路径日志缺 status/channel/tokens）。选单写者收敛。
- **落库请求体保留原始 body（P2 语义）**：否决（本轮）——"敏感信息不进库"；阻断/脱敏证据由 findings 打码+哈希承担，原始明文不再入库（P2 §9.3 语义升级，测试 #15/#16 断言随迁）。
- **token 缺失本地估算**：否决（Q8）——ADR-0010 已收敛"不估算、不伪造、不阻断"，估算值会污染配额账单。
- **DispatchOutcome 新增 failed 态 / 独立 503 对象**：否决——reason 枚举区分即可，routed=false 语义保持，ProxyCase 按 reason 映射 404/503。

## Consequences

- 组件改动：`ISecurityAuditCase`/`SecurityAuditCase` 降为纯决策；新增 `IDataPlaneAuditCase`/`DataPlaneAuditCase`（cases/audit，api 根包）；`ProxyCase` 七步串联 + 全量记日志 + 流式三分支回调；`ChannelDispatchServiceImpl` 零渠道 503 + retry-cap；`DispatchOutcome` 加 reason；SSE tee 扩展累积；配置 `dispatch.retry-count`/`request-log.policy`。
- schema **零改动**（request_logs 字段已全）。
- 既有收敛影响：ADR-0012 加 Update 注记（retry-cap、503 区分、DispatchOutcome.reason）；P2 的 #15/#16 断言迁移到单写者链路并升级脱敏语义；GLOSSARY 新增 ProxyCase/静默化/日志保留档并修订 RequestLog（已落）。
- 测试：16 条新增（app/src/test 按被测包路径，@SpringBootTest 真实容器 + MockWebServer，入参出参 log+JSON）；现有 ProxyCaseTest / ChannelDispatchServiceTest / RequestLogCaseTest 保持全绿。
- 已知缺口：warn 档 Allow 请求的转发体未脱敏、落库为原文（策略档位所致，redact 档覆盖）；流式响应体不落库（透传红线）；自动保留期清理未做。
