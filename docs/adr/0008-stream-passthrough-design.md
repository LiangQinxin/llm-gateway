# 0008 数据面流式响应透传机制（字节透传 + 显式取消）

ADR-0007 的 Q15 落地细化：`/v1/chat/completions` 同一条链路在 stream=true/false 下统一用 `ResponseBodyEmitter`，本 ADR 收敛流式判定的权威来源、非 2xx 错误透传、SSE 透传形态与断连取消机制（grill R1–R3）。

**Status**: accepted

## 决策要点

- **流式判定权威（R1-Q1）**：请求体 `stream` 字段为唯一权威；`Accept` header 仅在 body 缺省时兜底（null-safe + `MediaType` 兼容判断，禁裸字符串 `.contains`）。
- **错误透传（R1-Q2）**：上游非 2xx → setStatus + `application/json` 错误体透传并 complete（不按 SSE 帧发）；2xx → SSE 逐块字节透传。
- **SSE 透传形态（R2-Q1=①）**：字节级透传，网关不解析不重编码；端口保持 `forwardStream → StreamResult{statusCode, InputStream}`，case 层逐块 `send(byte[])`；`data:` 前缀、`[DONE]`、`event:`、多行 data 随字节原样。
- **断连取消（R2-Q2）**：`StreamResult` 增加 cancel 句柄（OkHttp `Call`），`emitter.onCompletion/onError` → `call.cancel()`；未发字节前失败可回退 JSON 错误，已开流后失败用 `completeWithError()` 异常终止，不注入自造 SSE 提示帧。
- **返回类型（R2-Q4）**：固定 `ResponseBodyEmitter` + 注入 `HttpServletRequest/HttpServletResponse`；否决 `Object` 动态返回。
- **非流式字节保真（R1-Q4 收口）**：与流式同原则——`ForwardResult` 增加 `raw` String 原样透传（适配器 `forward()` 已有原始文本），`JSONObject` 仅用于 usage 提取与日志。
- **实现硬性要求**：流式先 `response.setContentType("text/event-stream;charset=UTF-8")` 再 `send(byte[])`（否则 converter 默认 `application/octet-stream`）；非流式先设 `application/json`；异步超时显式配置 `spring.mvc.async.request-timeout` ≥ 最坏单次流时长（待定参数，建议按渠道 `timeoutSecs` 加余量，全局默认 600000ms）；转发放 `applicationTaskExecutor`（ADR-0007）。

## Considered Options

- **解析重编码（OkHttp enqueue + onChunk 剥 `data:` 前缀）**：否决——`[DONE]` 被吞、非 2xx 错误体被丢、`startsWith("data: ")` 漏掉无空格/多行 data/`event:` 字段；网关变 codec，违背 ADR-0006/0007 透传定位。
- **Controller 返回 `Object`**：否决——Spring MVC 返回值处理器按声明类型静态选型，`Object` 下 emitter 走 MessageConverter 被序列化为 JSON，流式静默失效。
- **`SseEmitter`**：否决——自动重排帧（加 `data:` 前缀），破坏字节级透传。
- **两个 `produces` 方法路由（SSE / JSON）**：否决——按 Accept 路由而非 body `stream`，curl/`stream:true`+`Accept:*/*` 语义错误。
- **非流式 re-encode（JSONObject 经 Jackson 重序列化）**：内容不丢但键序/格式失真，错误体也被重编码，与透传原则不一致，否决。

## Consequences

- 同协议（OpenAI 兼容入站）全链路字节级零失真；跨协议（Claude/Codex 入站）响应转换属未来 codec 决策（ADR-0006 口子），返回机制不变。
- `StreamResult` 增 cancel 句柄、`ForwardResult` 增 raw String，两值对象各加一个字段，适配器改动极小。
- 全链路测试：适配器层沿用 DeepSeekAdaptorTest 形态；Controller 链路 `@SpringBootTest` 起真实容器，流式用流式客户端逐行断言 SSE 至 `[DONE]`，非流式断言 JSON 透传。
- 修正文档漂移：ADR-0006「流式响应经 WebClient 直连透传」→ 实际为 OkHttp 出站（ADR-0003）+ ResponseBodyEmitter 入站（本 ADR）。
