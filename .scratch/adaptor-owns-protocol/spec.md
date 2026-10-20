# Spec: 渠道适配器承担上游协议转换，撤销独立 protocol 模块

Status: ready-for-agent
Slug: adaptor-owns-protocol
Created: 2026-10-20
决策依据: ADR-0006（协议转换内聚于渠道适配器，撤销 ADR-0001 中独立 protocol 第 8 模块的决策）；术语见根目录 GLOSSARY.md

---

## Problem Statement

原 Rust WaLiAPI 项目存在双轨协议处理逻辑：legacy 适配器层做简单透传，同时新建了独立 protocol codec 模块做双向编解码。Java 版承接时需要决定是否照搬双轨结构：是新建独立 protocol 模块做统一编解码，还是把协议转换全部内聚到渠道适配器里？选错会导致后续双轨维护、重复造轮子与二次迁移成本。

## Solution

彻底撤销独立 protocol 模块，所有上游协议转换内聚到渠道适配器（channel 域 `ILlmAdaptor` 端口 + infrastructure 层各厂商实现）：OpenAI 兼容渠道（openai/deepseek/custom 等）直接透传请求并做模型映射；非兼容渠道（Claude Messages、Gemini）在适配器内部完成 OpenAI 格式请求 → 上游格式的转换与响应转换。入站侧统一 OpenAI 协议，本期不做入站多协议检测，SSE 流式字节透传由 proxy 层执行编排，不建独立 SSE codec 模块。

## User Stories

1. 作为开发者，我想项目不设独立 protocol 模块，所有上游协议转换逻辑都在对应厂商的渠道适配器实现内部，以便协议差异按厂商隔离、双轨合一。
2. 作为开发者，我想 channel 域定义统一的 `ILlmAdaptor` 端口，所有厂商适配器实现该接口（连通测试、非流式 forward、流式 forwardStream），以便路由层按渠道类型分发。
3. 作为开发者，OpenAI 兼容渠道（DeepSeek/OpenAI/Custom）适配器直接透传请求体，只做鉴权头注入、端点路径拼接与模型映射，以便简单渠道零转换开销。
4. 作为开发者，非 OpenAI 兼容渠道（Claude/Gemini）适配器内部完成 OpenAI Chat Completions 请求 → 上游原生格式的转换，响应再转回 OpenAI 兼容结构，以便下游客户端统一用 OpenAI 协议调用。
5. 作为开发者，我想渠道连通性测试逻辑也统一在适配器内部（各厂商对应各自的探测端点与鉴权头），以便新增厂商适配器时自包含连通能力。
6. 作为开发者，我想模型映射（ModelMapping）应用在适配器内部，字符串一对一映射直接改写 body.model，数组采样已在路由阶段预烘焙，适配器不重复采样。
7. 作为开发者，我想 SSE 流式响应不做帧解析重编码，字节级原样透传（ADR-0008），网关不解析 data: 前缀、不重组帧，以便避免 `[DONE]` 丢失、多行 data 吞掉等兼容性问题。
8. 作为开发者，入站侧本期只支持 OpenAI 协议（/v1/chat/completions），不做入站协议检测与 Claude/Gemini 入站端点，以便减少 inbound codec 复杂度。

## Implementation Decisions

- **撤销独立 protocol 模块**：原 ADR-0001 中"第 8 模块 llm-gateway-protocol"的决策正式作废，项目固定 7 模块骨架。
- **适配器端口定义（channel 域）**：`ILlmAdaptor` 统一三个方法：
  - `testConnection(ChannelConfig cfg)`：连通性探测；
  - `forward(ProxyRequest req, ChannelConfig cfg)`：非流式转发，返回 `ForwardResult`（含 raw 原始响应字节）；
  - `forwardStream(ProxyRequest req, ChannelConfig cfg)`：流式转发，返回 `StreamResult`（含 InputStream 字节流 + cancel 句柄）。
- **实现归属**：所有适配器实现放在 infrastructure 层，按厂商命名 Bean（如 `deepSeekAdaptor`、`claudeAdaptor`），Spring 启动时注入 `Map<String, ILlmAdaptor>` 供路由按 channel.type 查找。
- **协议转换边界**：
  - OpenAI 兼容上游：无格式转换，直接透传 JSON body，仅注入鉴权头、替换 base_url、应用模型映射；
  - 非兼容上游：适配器内部完成请求格式转换（OpenAI Chat → 上游 Messages/Gemini 格式），响应再转回 OpenAI 结构；
  - SSE 流式：无论哪种上游，出站拿到字节流后不解析、不重编码，直接向上透传。
- **入站协议**：统一 OpenAI 兼容，不做入站多协议检测；后续如需支持 Claude/Gemini 入站，再在 case 层加对应 MessagesCase 做双向转换（届时另立 ADR）。
- **请求清洗（sanitize）**：空 content assistant 消息清洗留在 proxy 层入站侧，所有适配器共享，不重复实现。

## Testing Decisions

- **测试接缝**：复用项目唯一接缝——@SpringBootTest 起真实容器，适配器层测试连真实上游（或本地 mock 上游）验证转发与转换正确性。
- **测试位置**：适配器实现测试放 app/src/test 对应包路径下。
- **好测试标准**：只验证外部行为——适配器对 OpenAI 兼容上游透传结果正确、非兼容上游请求转换后字段正确、流式 SSE 字节原样透传、连通测试正确返回成功/失败；不断言适配器内部转换的私有方法。
- **先例**：现有 DeepSeekAdaptorTest 为适配器测试样板（连通、非流式转发、流式 SSE 逐块断言至 [DONE]）。

## Out of Scope

- 入站多协议支持（/v1/messages Claude 入站、Gemini 入站端点）——本期不做，留待后续 codec 决策。
- 独立 SSE 编解码模块——不建，字节透传为硬约束。
- 适配器层重试、熔断、限流——留待后续 proxy 编排层扩展。
- 全部厂商适配器实现（OpenAI/Claude/Gemini）——本期先落地 DeepSeek 适配器打通链路，其余随 preset 注册表阶段补。

## Further Notes

- 本 ADR 修正了 ADR-0001 中独立 protocol 模块的决策，后续不得新建第 8 个协议模块。
- 网关定位是"透传代理"而非"协议中台"：同协议全链路字节级零失真，跨协议转换按厂商适配器内聚，不做通用 codec。
- 后续若真有跨厂商复用的编解码逻辑，再考虑提取公共工具类，但不上升为独立模块。
