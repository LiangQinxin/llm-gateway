# 0007 数据面转发链路设计（/v1/chat/completions 一条链路）

数据面转发链路（ADR-0006 裁定协议转换内聚于渠道适配器之后）的范围与响应机制在 grill 收敛（Q14–Q17）中定案：本期只打通 `/v1/chat/completions` 一条链路（`responses`/`messages`/`completions` 留路由位不实现），转发编排集中在 `ProxyCase`（Controller 薄转发），流式/非流式统一用 `ResponseBodyEmitter` 透传上游响应，渠道选择按请求 model 全查启用渠道取第一个命中（暂不加权重）。

**Status**: accepted

## 决策要点

- **Controller 薄转发**：`POST /v1/chat/completions` 只做解析与写响应，编排在 case 层 `ProxyCase`；入站 body 用 `@RequestBody String` + fastjson 解析（项目无 fastjson HttpMessageConverter，避免 Jackson 对 JSONObject 的兼容问题）。
- **统一 ResponseBodyEmitter（Q15）**：同一端点 stream=true/false 均可；注入 `HttpServletRequest`（拿请求路径/审计信息）与 `HttpServletResponse`（直接控制状态码与 Content-Type）；非流式 send JSON 后 complete，流式逐块透传 SSE 字节后 complete；转发放异步线程执行，不阻塞 Tomcat 线程。
- **透传不包装（Q15）**：数据面原样透传上游状态码与错误体（含 4xx/5xx，OpenAI 风格 `{"error":{"message":...}}`），不套管理面 `Response<T>`——下游是 OpenAI 兼容 SDK。
- **sanitize 本期做（Q16）**：空 content 的 assistant 消息清洗（无 tool_calls 整条删，有 tool_calls content 置 null），防 OpenAI 兼容上游 400。
- **渠道选择（Q17）**：`SELECT * FROM channels WHERE status=1 ORDER BY priority DESC, weight DESC` 全查，服务内定义 `ChannelSelection{channel, upstreamModel, success, errorMessage}`，按请求 model 匹配（渠道 models 包含，或 model_mapping key 命中并经 ModelMapping.resolve 一次采样）取第一个命中。
- **模型映射归属（Q8=②）**：字符串映射改写归适配器（`OpenAICompatSupport.applyModelMapping`）；数组映射采样归 proxy 路由阶段一次预烘焙进 body，适配器不重采样（保证日志 upstream_model 与请求 model 同源）。

## Considered Options

- **响应机制**：否决 `StreamingResponseBody` 与「用返回值控制响应」（后续客户端不一，需注入 response 直接控制状态码/Content-Type）；否决数据面用 `Response<T>` 包装（下游是 OpenAI 兼容 SDK，需上游风格错误体）。
- **渠道选择**：否决加权分配/负载均衡（本期只按 model 打通一条链路，先取第一个命中，权重路由留待后续轮次）。
- **入站解析**：否决 `@RequestBody JSONObject`（Jackson 对 fastjson JSONObject 反序列化存在兼容风险），改 `String` + fastjson 解析。

## Consequences

- 转发链路由 case/trigger 层依赖异步线程池（Spring Boot 自动配置的 `applicationTaskExecutor`）；数据面端点不在管理面 `/admin/api/**` 鉴权路径内。
- 上游服务（`127.0.0.1:8777/v1`，模型 `deepseek-flash`）是外部测试事实，非本库产物；验证该链路需该服务在线。
- 渠道选择结果实体 `ChannelSelection` 与路由采样逻辑属 proxy 域（route_plan 简化版），落地时放 `domain/proxy` 域；转发结果 `ProxyResult` 同域。
