# ADR-0011: 入站三协议族（cc/rp/ms）统一入口，适配器内聚双向 codec

Status: accepted（grill 第二轮收敛；**修订 ADR-0010 §3 入站 body 转换位置**）

## Context

- 现状入站只有 `/v1/chat/completions`（cc）。本期开放 `/v1/responses`（rp）与 `/v1/messages`（ms）两个入站端点；客户端用什么协议打进来，响应就回什么形状（ grill Q2 拍板）。
- 厂商 × 协议矩阵（已逐格核对）：**列=协议**（openai 族 Bearer：cc+rp；anthropic 族 x-api-key：ms；ollama 族 OptionalBearer：ac），**行=厂商**（决定 base_url/模型/region）。同一厂商可跨列：deepseek/qwen/zhipu 同时支持 cc 与 ms；doubao_coding_plan 只有 ms；openai/qwen/ollama/custom 支持 cc+rp。
- 时序硬伤：body 转换方向取决于**命中渠道的出站协议**，而渠道选择发生在 `IDispatchInvoker` 黑盒内部（ADR-0009），ProxyCase 无法预知。若 case 层先转换，ms 入站打到 ms 原生渠道会被迫 ms→cc→ms 双重转换——既浪费又伤保真。
- 保真红线（ grill 拍板）：转换只换 JSON 信封，正文 delta 字节级原文拷贝；未知字段进扩展袋不丢；目标协议不支持的功能性字段显式 400 `unsupported_field`，元数据类宽容（Q10=c）。

## Decision

### 1. 入站协议族 = Strategy + Registry（case 层）

按 **path** 注册协议族策略 `{key 提取, model 提取, 错误体形状}`：

| 族 | 端点 | 鉴权 | model 位置 | 错误体 |
|---|---|---|---|---|
| cc | `/v1/chat/completions` | Bearer | `body.model` | OpenAI |
| rp | `/v1/responses` | Bearer | `body.model` | OpenAI |
| ms | `/v1/messages` | `x-api-key` + `anthropic-version` | `body.model` | Anthropic `{"type":"error",...}` |

cc/rp 族错误体为 OpenAI 形状；ms 族为 Anthropic 形状（ADR-0010 §6 已留口）。

### 2. 单入口汇聚

`/v1/chat/completions`、`/v1/responses`、`/v1/messages` 三个 POST 端点全部汇聚到 `ProxyCase` 同一编排：prepare（解析 + 按族提取 key/model）→ 鉴权/配额（identity 域）→ forward（调 `IDispatchInvoker`）→ write。Controller 只做收发包。

### 3. ProxyRequest 携带入站协议标签（P_in）

dispatch 选渠道后把 P_in 透传给适配器。channel 域不解析 body，只透传标签。

### 4. 适配器内聚出站转换（ADR-0006 不变）

每个适配器子类声明**有序原生端点表** `nativeEndpoints()`：每条目 `{协议, 端点路径, 鉴权方式}`，表中第一个 = primary。父类模板方法统一决策：

- **P_in 按协议命中条目**：原生透传该端点路径，请求体 / 非流式响应 / SSE 字节**零转换**；鉴权头按条目构造（cc/rp→Bearer，ms→x-api-key+anthropic-version）；
- **未命中**：目标 = 表中第一个条目；经共享 codec 以 canonical（= cc 形状）中转转换（目标非 cc 时多一跳），响应反向逐级还原为 P_in 形状。

示例：deepseek 端点表 `[{cc,/v1/chat/completions,Bearer},{ms,/anthropic/v1/messages,x-api-key}]`——rp 入站未命中 → 转第一个（cc，一跳 RpCodec）；ms 入站命中 → 零转换透传 ms 端点。

适配器注册表仍按 `channel.type` 分发（`Map<type, adaptor>` 现状不变）。

### 5. 共享 codec 双向（canonical 中转，杜绝 N×M）

只写两个 codec，位置 `infrastructure.adapter.port.llm.codec`，每个双向覆盖三种形态（请求 JSON / 非流式响应 / SSE 帧逐帧映射）：

- `MsCodec`：ms ↔ cc（system 提取、content blocks↔messages、`input/output_tokens`↔`prompt/completion_tokens`、SSE `content_block_delta`→chunk）；
- `RpCodec`：rp ↔ cc（`instructions`↔system、`input`↔messages、`output` 数组↔choices、`usage.input_tokens`↔`prompt_tokens`）。

任意入站 × 出站组合 = codec 组合（例：rp 入站 + ms 出站 = RpCodec→MsCodec 两跳）。新增协议族只加一个 codec。

### 6. channels 表不加 protocol 列（Q7 拍板）

同一厂商多个协议端点 = **多条渠道记录**（不同 base_url/type）；厂商 × 协议能力对照按代码级维护（即 grill 中所述"策略枚举"，内聚在适配器的有序原生端点表 `nativeEndpoints()`，不另建表）。后续若要同渠道多端点，再另立 ADR 加列。

### 7. Gemini 走 OpenAI 兼容端点

google 厂商走 `/v1/chat/completions`（OpenAI 兼容端点），即 openai 族渠道；**不建原生 generateContent 适配器**（grill Q9 拍板）。

### 8. 修订 ADR-0010 §3

入站 **body 转换**由"case 层 codec"调整为"**适配器内组合共享 codec**"；case 层只保留协议族策略（key 提取 / model 提取 / 错误体形状）。原因：dispatch 黑盒时序（Context 段）。ADR-0010 §3 的"入站与厂商无关、不放适配器以免 N×M"结论不变——N+M 由共享 codec 保证。

## Considered Options

- **case 层统一 body codec（ADR-0010 原案）**：否决。dispatch 黑盒内部才知命中渠道，case 提前转换必然造成 ms 原生渠道的双重转换；case 感知渠道 = 泄漏渠道知识，破 ADR-0009 门面。
- **每适配器各自实现 ms↔cc / rp↔cc**：否决。openai 适配器与 anthropic 适配器都需要 ms↔cc，重复实现；共享 codec 消除重复。
- **channels 加 protocol 列**：本期否决（Q7）；留作后续同渠道多端点的口子。
- **新建"转换域"**：否决。协议族策略已在 proxy 域，body 转换已内聚 channel 适配器，无新域必要。
- **适配器按入站协议直接转换成对组合（不经 canonical）**：否决。N 入站 × M 出站 = N×M 转换类；canonical=cc 中转收敛为 N+M。

## Consequences

- 新增：三端点 Controller 映射、协议族策略注册表（case 层）、`ProxyRequest` 入站协议标签、`MsCodec`/`RpCodec`（含 SSE 帧转换）、各适配器 `nativeProtocols()`。
- `AnthropicAdaptor` 从"只有连通性 test"升级为完整适配器（ms 原生透传 + cc/rp 入站转换）；`OpenAICompatibleAdaptor` 保留 cc/rp 原生透传 + ms/rp 入站转换。
- 出站 HTTP 执行集中到 `infrastructure.gateway`（GatewayHttpClient，共享连接池， grill Q4 拍板）；抽象基类 `AbstractLlmAdaptor` 骨架（Template Method）落位。
- 测试：`app/src/test` 下 `@SpringBootTest` 真实容器 + MockWebServer 整链路，断言正文文本原文相等、usage 数值 1:1、SSE 帧序列逐条一致。
- 已知缺口：ac（ollama 原生 /api/chat）本期不做；rp 入站打 ms 渠道为两跳转换（经 cc 中转，无保真损失）。
