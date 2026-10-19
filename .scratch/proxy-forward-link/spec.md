# Spec: 数据面转发链路（/v1/chat/completions 一条链路）

Status: ready-for-agent
Slug: proxy-forward-link
Created: 2026-10-19
决策依据: ADR-0006（协议转换内聚于渠道适配器）、ADR-0007（转发链路设计）、ADR-0008（数据面流式响应透传机制：字节透传 + 显式取消，细化 Q15）；术语见根目录 GLOSSARY.md；渠道选择/模型映射/响应机制决策在 grill 逐轮收敛（Q8=②、Q14~Q17）

---

## Problem Statement

Java 版 llm-gateway 已落地三表基础（channels / api_keys / request_logs）与渠道适配器最小闭环（DeepSeekAdaptor 连通/转发/流式已真实验证），但数据面（/v1/*）仍无任何转发能力：下游 OpenAI 兼容 SDK 无法通过本网关调用上游 LLM。本 spec 打通"一条链路"：入站 `/v1/chat/completions` → Controller 薄转发 → ProxyCase 编排（按请求 model 选渠道）→ 渠道适配器（当前仅 DeepSeek）→ 真实上游 → 透传返回（流式/非流式统一），作为后续其余端点、其余适配器与密钥鉴权的样板。

## Solution

六边形分层 + 用例编排：domain/proxy 域承载路由决策（渠道选择结果、转发结果、选择器，纯决策无框架依赖）；channel 域仓储端口扩展启用渠道查询；infrastructure 扩展 MyBatis 查询与请求清洗（sanitize）；case 层新增 ProxyCase 编排（选渠道 → 转换 ChannelConfig → 适配器 forward/forwardStream）；trigger 层新增 ProxyController（`POST /v1/chat/completions`，流式/非流式统一 ResponseBodyEmitter，注入 request/response）。数据面纯透传：上游状态码与错误体原样返回，不套管理面 `Response<T>` 包装。流式/非流式透传机制按 ADR-0008 收口：请求体 `stream` 为流式判定唯一权威（`Accept` 仅 body 缺省时兜底）、上游非 2xx 以 `application/json` 错误体透传、SSE 字节级透传（网关不解析不重编码）、断连显式取消上游调用、非流式原始字节保真。

## User Stories

1. 作为下游应用（OpenAI 兼容 SDK 客户端），我想以 `stream=false` POST `/v1/chat/completions`，以便获得非流式补全结果（choices/usage）。
2. 作为下游应用，我想以 `stream=true` POST `/v1/chat/completions`，以便获得 SSE 流式输出（逐块透传，含 `data:` 行与 `[DONE]` 结束符）。
3. 作为下游应用，我想在请求体中指定 model，以便网关按 model 路由到对应渠道（Q17：全查取第一个命中，不做加权分配）。
4. 作为网关，请求 model 命中渠道 models 列表时，我想直连该渠道并以原模型名转发，以便模型级路由。
5. 作为网关，请求 model 命中渠道 model_mapping 的 key 时，我想改写为上游模型名（字符串一对一映射，在适配器内应用），以便下游可用任意模型名。
6. 作为网关，映射值为数组时，我想在路由阶段一次采样预烘焙进请求体（Q8=②），以便日志 upstream_model 与真实请求模型同源、适配器不重采样。
7. 作为网关，我想流式响应以 `text/event-stream` 逐字节透传（okhttp 字节流 → emitter），以便客户端无缝消费 SSE。
8. 作为网关，我想非流式响应以 `application/json` 透传上游 JSON（含 4xx/5xx 错误体 `{"error":{"message":...}}`），以便 SDK 正常解析失败。
9. 作为网关，渠道未命中时我想返回 404 OpenAI 风格错误体（`no channel for model: xxx`），以便客户端明确感知路由失败。
10. 作为网关，请求体缺 model 时我想返回 400 OpenAI 风格错误体，以便客户端修正请求。
11. 作为网关，我想对空 content 的 assistant 消息做 sanitize（Q16：无 tool_calls 整条删、有 tool_calls content 置 null），以便避免 OpenAI 兼容上游 400 拒绝。
12. 作为网关，我想在 Controller 注入 HttpServletRequest 记录请求路径与 model/stream，以便审计追踪。
13. 作为网关，我想流式/非流式统一走 ResponseBodyEmitter（Q15），同一端点兼容不同客户端；响应经注入的 HttpServletResponse 直接控制状态码与 Content-Type。
14. 作为网关，我想数据面不套 `Response<T>` 包装、不经过管理面鉴权路径，以便保持 OpenAI 协议兼容形态。
15. 作为网关管理员，我想对渠道发起连通性测试（既有 test_channel），以便路由前确认上游可用。
16. 作为下游应用，我想仅以请求体 `stream` 字段决定响应形态，以便 `stream:true` 配 `Accept:*/*` 也能正确收到 SSE（R1-Q1：body stream 唯一权威）。
17. 作为下游应用，请求体缺省 stream 时，我想让网关按 `Accept` 头兜底判定流式/非流式，以便兼容只发 Accept 的客户端（null-safe + MediaType 兼容判断，禁裸字符串 contains）。
18. 作为网关，上游返回非 2xx 时，我想以相同状态码 + `application/json` 错误体原样透传并 complete，以便客户端 SDK 正确解析失败（不按 SSE 帧发）。
19. 作为网关，上游 2xx 时，我想按 SSE 逐块字节透传（`data:` 前缀、`[DONE]`、`event:`、多行 data 原样随字节），以便客户端无缝消费上游原始流。
20. 作为网关，客户端断开连接时，我想取消上游 OkHttp 调用，以便及时释放上游资源（R2-Q2：emitter onCompletion/onError → call.cancel()）。
21. 作为网关，已开流后上游中途失败时，我想以 completeWithError() 异常终止，以便客户端按协议感知中断（不注入自造 SSE 提示帧）。
22. 作为网关，非流式响应我想按上游原始文本字节透传（ForwardResult.raw），以便键序/格式零失真；JSONObject 仅用于 usage 提取与日志。
23. 作为网关，流式响应我想先设 `text/event-stream;charset=UTF-8` 再 send 字节，以便不被 converter 降级为 `application/octet-stream`。
24. 作为网关管理员，我想为异步转发放置充足超时（`spring.mvc.async.request-timeout` ≥ 最坏单次流时长），以便长流不被容器超时截断。

## Implementation Decisions

- **模块改动**（本期新增/扩展，落地以本 spec 为准，不得早于 spec 发布动代码）：
  - domain/channel：`IChannelRepository` 扩展 `queryEnabledOrdered()`（status=1 ORDER BY priority DESC, weight DESC 全查）；
  - domain/proxy：新增值对象 `ChannelSelection{channel, upstreamModel, success, errorMessage}`（Q17 用户定形）、`ProxyResult{statusCode, body, stream}`（stream 非空即流式）；新增域服务 `ChannelSelector`（按 model 匹配：渠道 models 包含 → 直连；model_mapping key 命中 → `ModelMapping.resolve` 一次采样得上游模型名；取第一个；未命中 errorMessage）；
  - infrastructure：`ChannelMapper`/`channel_mapper.xml`/`ChannelRepository` 扩展 selectEnabledOrdered；`OpenAICompatSupport` 扩展 `sanitizeMessages`（Q16）；`DeepSeekAdaptor` 的 forward/forwardStream 先 sanitize 后 applyModelMapping；
  - case：新增 `cn.qx.cases.proxy.ProxyCase`（编排：校验 model → 查启用渠道 → ChannelSelector → 预烘焙 upstreamModel 进 body → `toChannelConfig()` → `IChannelAdaptorRegistry.getAdaptor(type)` → forward/forwardStream → ProxyResult）；
  - trigger：新增 `cn.qx.trigger.proxy.ProxyController`（`POST /v1/chat/completions`）。
- **入站解析**：`@RequestBody String` + fastjson `JSON.parseObject`（项目无 fastjson HttpMessageConverter，避免 Jackson 对 JSONObject 反序列化兼容风险）；否决 `@RequestBody JSONObject`。
- **响应机制（Q15 + ADR-0008 R2-Q4）**：固定返回类型 `ResponseBodyEmitter`（否决 `Object` 动态返回——返回值处理器按声明类型静态选型，Object 下 emitter 走 MessageConverter 被序列化为 JSON，流式静默失效）；方法签名注入 `HttpServletRequest`（路径/审计）与 `HttpServletResponse`（setStatus/setContentType）；转发放异步线程（Spring Boot 自动配置 `applicationTaskExecutor`），不阻塞 Tomcat 线程；流式逐块 `send(byte[])` 后 complete，非流式 send JSON 后 complete。
- **Content-Type 前置（ADR-0008 硬性要求）**：流式先 `response.setContentType("text/event-stream;charset=UTF-8")` 再 send（否则 converter 默认 `application/octet-stream`）；非流式先设 `application/json` 再 send。
- **流式判定权威（ADR-0008 R1-Q1）**：请求体 `stream` 字段为唯一权威；body 缺省时 `Accept` 头兜底（null-safe + `MediaType` 兼容判断，禁裸字符串 `.contains`）；否决按 Accept 路由的双 produces 方法（curl / `stream:true`+`Accept:*/*` 语义错误）。
- **错误透传（ADR-0008 R1-Q2）**：上游非 2xx → `setStatus(上游码)` + `application/json` 错误体透传后 complete（不按 SSE 帧发）；2xx → SSE 逐块字节透传。
- **SSE 透传形态（ADR-0008 R2-Q1=①）**：字节级透传，网关不解析不重编码；端口保持 `forwardStream → StreamResult{statusCode, InputStream}`，case 层逐块 `send(byte[])`；`data:` 前缀、`[DONE]`、`event:`、多行 data 随字节原样；否决解析重编码（剥 data: 前缀会吞 `[DONE]`、丢非 2xx 错误体、漏无空格/多行 data/`event:`）与 `SseEmitter`（自动重排帧破坏字节保真）。
- **断连取消（ADR-0008 R2-Q2）**：`StreamResult` 增 cancel 句柄（OkHttp `Call`），`emitter.onCompletion/onError` → `call.cancel()`；未发字节前失败可回退 JSON 错误体，已开流后失败用 `completeWithError()` 异常终止，不注入自造 SSE 提示帧。
- **非流式字节保真（ADR-0008 R1-Q4 收口）**：`ForwardResult` 增 `raw` String 原样透传（适配器 `forward()` 已有原始文本），`JSONObject` 仅用于 usage 提取与日志；否决 JSONObject 经 Jackson 重序列化（键序/格式失真、错误体被重编码）。
- **异步超时（ADR-0008）**：显式配置 `spring.mvc.async.request-timeout` ≥ 最坏单次流时长（参数待定：建议按渠道 `timeoutSecs` 加余量，全局默认 600000ms）。
- **透传契约**：上游状态码与错误体原样透传（含 4xx/5xx）；未命中渠道 404、缺 model 400，均为 `{"error":{"message":...}}`；否决数据面 `Response<T>` 包装、`StreamingResponseBody`、返回值控制响应。
- **映射归属（Q8=②）**：字符串改写归适配器（applyModelMapping）；数组采样归 proxy 路由一次预烘焙（不重采样，保证日志同源）。
- **Schema**：不变（channels 15 列，ADR-0004/0005；timeout/协议身份等扩展字段本期不引入）。
- **API 契约**：数据面 `POST /v1/chat/completions`，不在管理面 `/admin/api/**` 鉴权路径内。

## Testing Decisions

- **测试接缝（单一接缝，复用既有）**：`@SpringBootTest` 起真实 Spring 容器连本地 MySQL（llm_api）+ 真实上游 `127.0.0.1:8777`，从 Case/Controller 层跑整链路；不 mock 任何 Bean、不手工装配——与 ADR-0005、three-table-baseline 先例一致。本 spec 不新增接缝。
- **测试位置**：统一放 app/src/test，按被测包路径建包（`cn.qx.cases.proxy.ProxyCaseTest`、`cn.qx.trigger.proxy.ProxyControllerTest`），禁止放 domain 等模块 test 目录。
- **好测试标准**：只验证外部行为（状态码、响应体 choices/usage、SSE 字节流含 data: 与 [DONE] 原样、非 2xx `application/json` 错误体透传、404/400 错误体、映射改写效果），不断言内部实现细节（断连取消只断言可观察结果）；入参出参 log + JSON 打印。
- **覆盖边界**：非流式 200（真实转发，raw 字节保真）、流式 SSE 到 `[DONE]`（流式客户端逐行断言 `data:` 前缀/[DONE]/event:/多行 data 原样，字节级）、上游非 2xx 错误透传（断言状态码 + `application/json` 错误体，非 SSE 帧）、流式判定权威（`stream:true`+`Accept:*/*` → SSE；body 缺省 + Accept 兜底）、字符串映射命中、数组映射采样命中、渠道未命中 404、缺 model 400、Controller 端点契约（MockMvc + ResponseBodyEmitter 异步链路 asyncStarted → asyncDispatch 的 400/404 分支）。适配器层沿用 DeepSeekAdaptorTest 形态（流式逐块字节断言至 `[DONE]`）。
- **环境前置**：真实转发用例依赖 `127.0.0.1:8777` 上游在线（外部服务，非本库产物）与真实 MySQL；上游离线时仅 404/400 分支可跑。
- **先例**：DeepSeekAdaptorTest（连通/转发/字符串/数组映射/SSE 流式）、ChannelCaseTest（真实链路+回写）均为同缝既有样板。

## Out of Scope

- `/v1/responses`、`/v1/messages`、`/v1/completions` 等其余数据面端点（本期只留路由位，不实现）。
- 渠道加权分配/负载均衡路由（Q17：先按 model 打通，取第一个命中；权重路由后续轮次）。
- 两级 preset 注册表（ChannelProtocol / ChannelProvider / NativeEndpoint / AuthScheme / ModelSuggestion / ChannelTypeInfo）与剩余适配器（OpenAI / Claude / Custom）——已定未实施（Q3=A），后续轮次随 preset 注册表一并落地。
- 密钥鉴权（identity 域）、请求日志落库（audit 域）、安全扫描——均留各域阶段。
- 数据面流式断连重连、超时熔断、retry/backoff 策略。
- 跨协议（Claude/Codex 入站）响应转换——属 ADR-0006 预留的 codec 决策，返回机制（ResponseBodyEmitter 透传）不变。
- SSE 帧解析/重编码、`[DONE]` 归一化等 codec 行为——网关不是 codec，字节级透传为硬约束。
- channels 表列扩展（Q4=B：15 列保持，扩展信息进 config JSON）。

## Further Notes

- **外部测试事实**：上游端点 `http://127.0.0.1:8777/v1`、apiKey `sk-waliapi-04dc74ce989d458e848a2c1616bf5611`、模型 `deepseek-flash`（GET /v1/models 实测在线）；验证真实转发需该服务在线。
- **已交付基础（不动）**：DeepSeekAdaptor（test/forward/forwardStream）、ChannelAdaptorRegistry、ModelMapping（resolve 数组采样）、Channel 实体（15 列 + activeModels/activeModelMapping/activeConfig/toChannelConfig）、test_channel 管理面、app/dev 数据源指向 llm_api。
- **实现顺序**：① channel 域仓储扩展（domain 端口 → infrastructure Mapper/XML/Repository）→ ② domain/proxy 值对象与 ChannelSelector → ③ OpenAICompatSupport.sanitizeMessages + DeepSeekAdaptor 接入 → ④ ProxyCase → ⑤ ProxyController（ResponseBodyEmitter）→ ⑥ 测试（ProxyCaseTest 真实转发 + ProxyControllerTest 端点契约）→ 跑通闭环。
- 本 spec 发布前曾有一次临时落地被退回（先固化 spec 再动代码，符合项目"收敛确认前不动代码"约定）；实现以本 spec 与 ADR-0007/0008 为准。
- **文档漂移修正（ADR-0008）**：ADR-0006「流式响应经 WebClient 直连透传」与实际实现不符——出站为 OkHttp（ADR-0003），入站为 ResponseBodyEmitter（ADR-0008）。
- **值对象增量**：`StreamResult` 增 cancel 句柄、`ForwardResult` 增 `raw` String，各加一个字段，适配器改动极小（forward() 原始文本已持有）。

## Comments

- 2026-10-19：并入 ADR-0008（数据面流式响应透传机制：字节透传 + 显式取消）——流式判定权威、错误透传、SSE 字节透传、断连取消、非流式字节保真、异步超时；测试接缝经确认复用本 spec 既有唯一接缝（@SpringBootTest 真实容器 + 真实上游），不新增。
