# llm-gateway

Java 版 LLM 网关，承接 Rust/Tauri 项目 WaLiAPI（v0.3.6）的功能面：渠道、密钥、转发主链路、安全扫描、知识库 RAG、wiki、MCP、auth_provider、语义缓存、OTLP、健康探针、审计日志。采用六边形分层（trigger→case→domain←infrastructure）+ 用例编排，对齐 llm-api 既有惯例。

## Language

**Channel（渠道）**:
连接上游 LLM 提供方的配置：类型（type 决定协议语义）、base_url、api_key、模型列表、优先级/权重、config 与 model_mapping JSON。
_Avoid_: upstream、provider、上游渠道

**ApiKey（密钥）**:
调用方身份凭证。全局唯一（key 列 uk 约束），携带模型/渠道白名单（空数组=全部允许）、配额（quota_limit=0 表示不限）与过期时间（expires_at 为空=永不过期）。
_Avoid_: token、API 凭证、api_key（列名保留）

**RequestLog（请求日志）**:
每次转发请求落一条的不可变审计记录，seq 自增保证顺序，只增不改；含安全审计字段（risk_level/risk_score/security_action 等）与请求/响应留存。
_Avoid_: audit row、log entry

**Proxy 域**:
转发主链路的编排层集合：入站解析、sanitize、identity 鉴权/配额、流监督（stream_supervisor）、单次尝试判定（attempt）、加权选 key（weighted_key，后续）、把结果绑到 ResponseBodyEmitter。**路由计划（route_plan）与渠道分发已下沉 channel 域（ADR-0009）**，不再是 proxy 域知识。HTTP 字节进出在 infrastructure，上游协议形状转换在渠道适配器（ADR-0006）。
_Avoid_: core、转发模块、route_plan（已迁出）

**IDispatchInvoker（转发门面）**:
channel 域对 ProxyCase 暴露的唯一转发入口端口（ADR-0009）。ProxyCase 只调它，不感知渠道查询、model 映射、适配器 map 或流式分支。唯一实现是路由器 service：内部串联「全查启用渠道 → 按 model 匹配 → 采样 upstreamModel → 烘焙 body.model → 按 channel.type 路由到 ILlmAdaptor → 按 stream 分支执行 forward/forwardStream」。
_Avoid_: dispatcher（保留给后续加权/重试编排）、router

**DispatchResult（路由决策中间值）**:
路由器在执行适配器之前产出的渠道域决策：`{ Channel channel, String upstreamModel, boolean success, String errorMessage }`。success=false 表示无渠道支持请求 model（404 model_not_found）；channel.type 无对应适配器属执行期失败（502 channel_type_unavailable）。
_Avoid_: ChannelSelection（ADR-0007 旧名，已废弃）、DispatchResultVO（VO 后缀不属 domain 层）

**Adaptor（适配器）**:
渠道对外部 LLM 供应商的协议能力：统一 OpenAI 入站请求的透传、模型映射、鉴权头与端点选择，以及非 OpenAI 兼容上游（Claude Messages、Gemini）的协议转换。协议差异内聚于适配器，不设独立协议翻译模块（ADR-0006）。
_Avoid_: codec、协议层（"protocol" 仅作 schema 中的协议标识列）

**ModelMapping（模型映射）**:
下游请求模型名与上游真实模型名的对应关系：字符串一对一映射；数组多目标按权重采样（Q8=②）。采样在 proxy 路由阶段执行一次并预烘焙进请求体（保证 日志 upstream_model = 实际请求模型 同源），适配器只应用映射结果、不自行采样。
_Avoid_: 模型改写、model_map

**ChannelSelection（渠道选择结果）**:
路由计划阶段按请求 model 从启用渠道中选出渠道的决策结果：选中渠道 + 上游模型名（含映射/采样后）+ 是否命中 + 未命中原因。取第一个命中（Q17，本期不做加权分配）。
_Avoid_: route、路由结果

**ProxyResult（转发结果）**:
proxy 域编排输出的协议无关响应：状态码 + 非流式 JSON body 或流式字节流（SSE 透传）；上游状态码与错误体原样透传，数据面不包装（ADR-0007）。
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

**quota（配额）**:
密钥额度语义：quota_limit=0 表示不限；quota_used 递增带封顶保护（超过上限后不再累加）。
_Avoid_: limit（列名 quota_limit 保留）、额度上限（表述需含"0=不限"）
