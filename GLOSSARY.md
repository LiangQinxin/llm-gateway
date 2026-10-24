# llm-gateway

Java 版 LLM 网关，承接 Rust/Tauri 原项目的功能面：渠道、密钥、转发主链路、安全扫描、知识库 RAG、wiki、MCP、auth_provider、语义缓存、OTLP、健康探针、审计日志。采用六边形分层（trigger→case→domain←infrastructure）+ 用例编排，对齐 llm-api 既有惯例。

## Language

**Channel（渠道）**:
连接上游 LLM 提供方的配置：类型（type 决定协议语义）、base_url、api_key、模型列表、优先级/权重、config 与 model_mapping JSON。
_Avoid_: upstream、provider、上游渠道

**ApiKey（密钥）**:
调用方身份凭证。全局唯一（key 列 uk 约束），携带模型/渠道白名单（空数组=全部允许）、配额（quota_limit=0 表示不限）与过期时间（expires_at 为空=永不过期）。
_Avoid_: token、API 凭证、api_key（列名保留）

**RequestLog（请求日志）**:
数据面每次转发请求落一条的不可变审计记录（全量留痕，ADR-0015；唯一例外=全库零启用渠道 503 不记），seq 自增保证顺序，只增不改。请求体落**脱敏后转发体**（敏感信息不进库，参考 WaLiAPI 贯穿式脱敏）；非流式响应落同构 `response_choices`，流式由旁路累积、终止时重建；含安全审计字段（risk_level/risk_score/security_action 等）与用量/耗时。
_Avoid_: audit row、log entry

**静默化（Silent Logging）**:
数据面留痕与配额记账的失败处理原则：写库失败 catch 记 warn，不影响主流程、不改变策略结果、不阻断透传（与 ADR-0010 best-effort 配额同款，ADR-0015 扩及全量日志与 findings）。唯一不记日志的业务分支：全库零启用渠道（503）。
_Avoid_: 吞异常、失败即中断

**日志保留档（log-policy）**:
落库前正文裁剪档位（detailed/brief/basic，参考 WaLiAPI 审计策略，ADR-0015）：detailed 原样保存；brief 请求 messages 只留最新 3 条；basic 正文不落库。只裁剪 requestBody/responseChoices，不影响索引字段与用量统计；裁剪失败回退原样（宁缺勿错）。
_Avoid_: 日志级别（log level 指日志框架级别）

**Proxy 域**:
转发主链路的编排层集合：入站解析、sanitize、identity 鉴权/配额、协议族解析、流监督（stream_supervisor）、单次尝试判定（attempt）、加权选 key（weighted_key，后续）、把结果绑到 ResponseBodyEmitter。**路由计划（route_plan）与渠道分发已下沉 channel 域（ADR-0009）**，不再是 proxy 域知识。HTTP 字节进出在 infrastructure，上游协议形状转换在渠道适配器（ADR-0006）。
_Avoid_: core、转发模块、route_plan（已迁出）

**ApiKeyFilter（数据面鉴权过滤器）**:
trigger 层过滤器（ADR-0010）：按免鉴权白名单直接放行，按协议族从请求头提取调用方 key（messages→x-api-key，其余→Bearer），取不到即 401 快速失败；不查库、不做白名单，完整认证在 case 层。
_Avoid_: AuthInterceptor、认证拦截器

**协议族（Protocol Family）**:
入站路径所属的协议维度：本期三族——cc（OpenAI Chat Completions）/ rp（OpenAI Responses）/ ms（Claude Messages）。决定调用方 key 的提取位置（ms→x-api-key，其余→Bearer）、model 的提取位置与错误体形状（ms→Anthropic 形状，其余→OpenAI 形状）；注册表以路径为键，model 是从 body 提取的输入而非键（ADR-0010/0011）。
_Avoid_: 协议类型（避免与渠道 type 混淆）、入站协议

**Canonical（经典中间格式）**:
协议转换的统一中转形状 = OpenAI Chat Completions（cc）请求/响应结构。入站 codec（rp/ms→cc）与出站 codec（cc↔ms）以它为唯一中间量，任意入站×出站组合 = codec 组合，杜绝 N×M 直接转换对；cc 入站且渠道原生支持 cc 时为零转换透传路径（ADR-0011）。
_Avoid_: 中间协议、标准格式

**IDispatchInvoker（转发门面）**:
channel 域对 ProxyCase 暴露的唯一转发入口端口（ADR-0009）。ProxyCase 只调它，不感知渠道查询、模型映射、适配器 map 或流式分支。唯一实现是路由器 service：内部串联「全查启用渠道 → ChannelSelector 构建 failover 队列 → 按序尝试（每渠道 per-attempt 采样烘焙 body.model，失败按分类切换下一渠道）→ 按 channel.type 路由到 ILlmAdaptor → 按 stream 分支执行 forward/forwardStream」（ADR-0012）。
_Avoid_: router

**ChannelSelector（渠道选择器）**:
channel 域调度器组件（service/selector 子包，接口 IChannelSelector + impl 实现，ADR-0012）：无状态纯函数，请求开始时对启用渠道做模型匹配过滤 → 优先级分组 → 组内不放回权重随机，构建每个渠道恰好出现一次的有序 failover 队列（即需求语境中的"调度器 / Dispatcher"）；编排按序消费，每渠道最多尝试一次，失败按分类切换下一渠道。
_Avoid_: dispatcher（职责已由 ChannelSelector 承担）、router

**DispatchOutcome（分发结果）**:
channel 域路由器一次表达三种结果：未命中 `routed=false + errorMessage`、非流式 `ForwardResult`、流式 `StreamResult`（含 cancel 句柄）。未命中是正常业务分支，返回 404 model_not_found；channel.type 无适配器由启动自检拦截。
_Avoid_: ChannelSelection（ADR-0007 旧名，已废弃）、DispatchResult、DispatchResultVO

**Adaptor（适配器）**:
渠道对外部 LLM 供应商的协议能力：统一 OpenAI 入站请求的透传、模型映射、鉴权头与端点选择，以及非 OpenAI 兼容上游（Claude Messages、Gemini）的协议转换。协议差异内聚于适配器，不设独立协议翻译模块（ADR-0006）。
_Avoid_: codec、协议层（"protocol" 仅作 schema 中的协议标识列）

**ModelMapping（模型映射）**:
下游请求模型名与上游真实模型名的对应关系：字符串一对一映射；数组多目标按权重采样（Q8=②）。采样在 channel 路由阶段执行一次并预烘焙进请求体（保证 日志 upstream_model = 实际请求模型 同源），适配器只应用映射结果、不自行采样。
_Avoid_: 模型改写、model_map

**ProxyResult（转发结果）**:
proxy 域编排输出的协议无关响应：状态码 + 非流式原始文本或流式字节流（SSE 透传）；上游状态码与错误体原样透传，数据面不包装（ADR-0007/0008）。类型位于 `api/dto/proxy`（case 输出对象归 api/dto，见 AGENTS.md「架构与约定」）；取消句柄以 Runnable 承载，api 层不引入 okhttp。
_Avoid_: GatewayResponse、统一响应

**StreamMode（流式模式）**:
单条转发请求的响应形态：流式（SSE 字节透传）或非流式（JSON）。由请求体的流式标志判定，网关不解析不重编码、随上游字节原样透传（ADR-0008）。
_Avoid_: stream flag、是否流式

**identity 域**:
调用方 API Key 的身份、鉴权、配额与白名单决策。
_Avoid_: auth、认证域

**account 域**:
上游第三方账号（auth_provider：codex/gemini/grok/kimi 的登录、刷新、配额）。
_Avoid_: user、用户域、账号域（避免与"账号"泛指混淆）

**Case（用例）**:
编排层组件（cn.qx.cases.<域>.<Xxx>Case），注入域服务/仓储端口，产出协议无关的 <Xxx>Result。Controller 只做「解析→调 Case→序列化」。
_Avoid_: service、facade、handler

**ProxyCase（转发编排用例）**:
proxy 域数据面转发编排用例，固定七步管道：解析模型 → 安全检查 → 找渠道 → 排队 → 循环转发 → 记日志 → 扣配额（ADR-0015）。鉴权/配额预检（401/403）在管道之前；成功产出结果对象，失败产出状态码+错误消息，状态码直接映射 HTTP 响应。职责分散——安全检查归 security 域（ISecurityAuditCase）、找渠道/排队/循环转发归 channel 域（IDispatchInvoker）、留痕归 audit 域（单写者 IDataPlaneAuditCase），ProxyCase 只串联不实现。
_Avoid_: Pase（拼写错误）、核心编排

**quota（配额）**:
密钥额度语义：quota_limit=0 表示不限；quota_used 递增带封顶保护（超过上限后不再累加）。认证时预检超限拒绝（403 insufficient_quota）；用量 best-effort——流式依赖上游 usage 帧（需请求带 stream_options），拿不到则不记、不估算、不阻断透传（ADR-0010）。
_Avoid_: limit（列名 quota_limit 保留）、额度上限（表述需含"0=不限"）

## 安全审计域

**SecurityScanner（安全扫描引擎）**:
security 域对请求/响应内容做安全审计判定的调度器：分派子扫描器检查文本、汇总发现、去重、按三段评分定风险等级并产出动作。
_Avoid_: 安全审计服务、auditor

**ISubScanner（子扫描器）**:
安全扫描的最小检查单元端口：输入一段文本，输出命中的安检结果列表；一个扫描器专注一类风险。
_Avoid_: 检测器、rule engine

**ScanPhase（扫描阶段）**:
扫描的方向维度：REQUEST（请求体）与 RESPONSE（响应体）；请求侧默认扫描，响应侧由 `scanResponse` 开关控制（默认关闭，开启后同样过扫描引擎）。
_Avoid_: 方向、audit phase

**ScanFinding（安检发现）**:
一次规则命中：规则 id、扫描器、严重度、类别标签、位置（JSONPath 或原文偏移）。命中证据分两层：`evidence` 完整原文（仅内部去重/定位，不参与 JSON 序列化）与 `evidenceMasked` 打码证据（对外展示，前 8+****+后 4、≤16 原样），审计日志与接口输出只出现打码版。
_Avoid_: 命中项、issue

**风险评分（三段制）**:
一次扫描的风险量化：基础分取最高 severity 值（Info=5/Low=15/Medium=35/High=65/Critical=90，不累加）→ 类别组合加分 → 总分 `min(100, 基础分+组合加分)` 封顶后反推等级（0=Clean、1–14=Info、15–34=Low、35–64=Medium、65–89=High、≥90=Critical，只升不降）。危险性不随命中数量线性叠加，超出 100 压回 100。
_Avoid_: 累加计分、风险分（表述需含三段制）

**策略档位（mode）**:
网关全局安全严格度档位（off/audit/warn/redact/confirm/block 六选一）；OFF/AUDIT/CONFIRM 只审计放行、WARN 中危以上警告、REDACT 高危以上脱敏、BLOCK 高危以上阻断；档内等级→动作映射查策略矩阵（代码内置常量），另配 blockCritical 开关（打开后 Critical 在任意档位强制 Block）。
_Avoid_: 安全模式（避免与 StreamMode 混淆）

**SecurityAction（安全动作）**:
一条发现最终动作的三值语义：Allow（放行并记录）、Sanitize（打码后放行）、Block（拦截）。落库值（RequestLog.securityAction）只用这三值。
_Avoid_: Warn、Redact（仅策略中间态，不落库）

**CredentialScanner（凭证扫描器）**:
检测密钥/账号密码泄露的子扫描器（云厂商密钥、私钥块、账号密码键值、带口令连接串等）。默认启用（可配置关闭）。

**PathScanner（路径扫描器）**:
检测路径攻击与敏感文件引用的子扫描器（路径穿越、~/.ssh、/etc/passwd 等）。默认启用（可配置关闭）。

**UnicodeScanner（Unicode 扫描器）**:
检测 Unicode 伪装字符的子扫描器（零宽字符、同形字、RTL 覆盖等）。默认关闭，配置启用。

**NetworkScanner（网络扫描器）**:
检测网络地址与内网探测的子扫描器（外联 URL、内网 IP/域名、端口等）。默认关闭，配置启用。

**ToolRiskScanner（危险工具扫描器）**:
检测危险工具调用的子扫描器（curl/wget 外发、base64 解码、反弹 shell 等）。默认关闭，配置启用。

**TrackingScanner（追踪扫描器）**:
检测追踪与指纹采集的子扫描器（跟踪像素、浏览器指纹、定位请求等）。默认关闭，配置启用。

**类别（ScanCategory）**:
ScanFinding 的风险类别标签，用于组合加分判断与归类：凭证、敏感文件、隐写、命令、外联、追踪。追踪类别不参与任何组合加分（组合表仅凭证/敏感文件/隐写/命令/外联四组）。
_Avoid_: 分类、tag（避免与审计域之外的概念混淆）

**内置规则（BuiltinRule）**:
应用播种的安全规则元数据（rule_id/category/severity/toggle_key/enabled，65 条逐条入库，rule_id 与子扫描器代码 id 一致）。只存"什么级别、开不开"，不存匹配模式——匹配逻辑仍在子扫描器代码（ADR-0014）。
_Avoid_: 规则表行、内置正则

**自定义规则（CustomRule）**:
用户维护的黑白名单（rule_type=blacklist|whitelist、category=domain|tool|path|keyword、pattern 子串匹配，禁用正则防 ReDoS）。黑名单命中产生 finding；白名单命中按类别映射豁免内置检测。
_Avoid_: 自定义正则、rule entry

**黑名单（Blacklist）**:
自定义规则的一种：文本子串命中即产生 `category="custom"` 的 finding，severity 并入统一评分矩阵处置（不绕过 blockCritical 兜底）。

**白名单（Whitelist）**:
自定义规则的一种：按类别映射豁免内置检测（domain→network、path→file、keyword→credential、tool→tool），不放宽到整段文本全豁免（防一条 path 白名单意外放掉凭证检测）。

**递归脱敏（RecursiveRedactor）**:
转发前对请求体做结构化 JSON 递归改写的脱敏器：敏感键名整值打码（键名级）、文本内密钥前缀按词打码（字符串级）、PEM 私钥整块替换；非法 JSON 降级原文正则兜底。maskString 保留前 4 后 4（`sk-1234…` → `sk-1****cdef`），比审计证据打码更彻底。
_Avoid_: 脱敏器、sanitizer（避免与既有 MessageSanitizer 混淆）

**双层开关（toggle_key）**:
内置规则与全局开关的关联键：规则生效 = `enabled=1` 且（toggle_key 为空 OR 对应类别开关开启）。四键映射：security.scan_unicode/scan_network/scan_tools/scan_tracking。
_Avoid_: 类别总开关（表述需含"规则级 + 类别级"两层）
