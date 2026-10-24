# ADR-0012: 渠道调度器 ChannelSelector——优先级分组 + 组内权重随机构建 failover 队列

多个启用渠道都能服务同一模型时，请求发给谁？ADR-0009 的 `ChannelDispatchServiceImpl` 目前取排序后的**第一个命中渠道**即转发、失败直接抛异常：weight 只参与排序不参与选择（无负载均衡），失败无兜底（无故障转移）。本 ADR 引入无状态纯函数调度器 `ChannelSelector`：请求开始时一次性构建**每个渠道恰好出现一次**的有序 failover 队列（模型匹配过滤 → 优先级分组 → 组内不放回权重随机），编排按序消费、每渠道最多尝试一次，失败按分类切换下一渠道——实现「优先级分组 + 组内权重随机」的混合策略，同时满足主备（priority）、分摊（weight）、无状态（权重随机而非轮询）三类诉求。

Status: accepted（取代 ADR-0009 §3 步骤 2/4/5 的「内存找第一个命中」与「采样在路由阶段一次预烘焙」表述；ProxyCase 门面边界、适配器路由、流式/非流式字节透传均不变）。

> **Update (ADR-0015)**：新增可选 `dispatch.retry-count`（默认 null=不设上限、沿用排空队列；有值时最大尝试次数=min(retry+1, 队列长度)）；全库零启用渠道返回 `DispatchOutcome.noChannel()`（503 no_available_channel，唯一不记日志分支）而非 notFound；`DispatchOutcome` 增加 reason 区分 MODEL_NOT_FOUND / NO_AVAILABLE_CHANNEL（未新增态）；失败分类表与透传语义不变。

## Decision

### 1. 组件结构与职责

```
domain/channel/service/
├── IChannelDispatchService / ChannelDispatchServiceImpl   ← 编排（改造现有）
└── selector/
    ├── IChannelSelector          ← 接口（I 前缀约定）
    └── impl/
        └── ChannelSelectorImpl   ← 无状态纯函数实现
```

- `IChannelSelector.buildFailoverQueue(List<Channel> enabledOrdered, String model, Random random) → List<Channel>`：纯函数，不触碰适配器、不触碰网络、不修改请求体；`random` 入参便于测试注入，生产传 `ThreadLocalRandom.current()`；
- `ChannelDispatchServiceImpl.invoke()` 只做编排：查启用渠道 → 调 selector 构建队列 → 按序消费。

### 2. 选择器算法（无状态纯函数）

1. **过滤**：`activeModels().contains(model)` ｜ `activeModels()` 为空（通配）｜ `activeModelMapping().containsKey(model)`，三者任一命中即入选；
2. **分组**：按 priority 降序分组，组间顺序即主备语义——高优先级组全部渠道排在任何低优先级组之前；
3. **组内不放回轮盘赌**：总权重 = Σ max(weight, 0)；weight 为 null 或 ≤0 **不参与**轮盘赌；组内总权重为 0（整组未配权重）**退化为按列表原顺序**（priority DESC, weight DESC）排列；每选出一个渠道即从组内移除，重复直至组内清空；
4. **拼接**：按 priority 从高到低拼接各组轮盘赌序列，weight=0 的渠道追加到队列尾部作最后兜底 → 完整 failover 队列，**每个渠道恰好出现一次**。

随机点法：`r = random.nextDouble() * totalWeight`，遍历组内渠道累加 weight，第一个使累计 > r 的渠道入选。

### 3. 编排改造（ChannelDispatchServiceImpl.invoke）

1. 查 `listEnabledOrdered()`（status=1 ORDER BY priority DESC, weight DESC，ADR-0009 不变）；
2. `queue = channelSelector.buildFailoverQueue(channels, model, random)`；队列为空 → `DispatchOutcome.notFound(model)`（404 model_not_found，不变）；
3. 遍历队列，对每个渠道：
   - 查适配器 `adaptors.get(type)`：无适配器 / 非 `IForwardingAdaptor` 跳过（启动自检已兜底，运行时视为 bug）；
   - **per-attempt 采样**：`new ModelMapping(ch.activeModelMapping()).resolve(model, random)` → `body.put("model", upstreamModel)`——**每次尝试前重新烘焙**（取代 ADR-0009「一次预烘焙」），保证日志 upstream_model 与实际请求模型同源；
   - 按 `request.isStream()` 调 `forward` / `forwardStream`；
4. 失败分类（见下表）：可转移 → 消费队列下一个；不可转移 → 透传终止；
5. 队列耗尽 → 透传**最后一个被尝试渠道**的失败。

### 4. 失败分类表（什么算"挂"）

| 失败类型 | 触发 failover | 说明 |
|---|---|---|
| 网络异常（连接超时/IO/SSL） | ✅ | 上游不可达 |
| HTTP 5xx | ✅ | 上游故障 |
| HTTP 429 | ✅ | 上游限流，切过去大概率能救 |
| HTTP 401/403 | ✅ | 密钥失效，兜底渠道 key 可能有效 |
| 其余 4xx（400/404/422） | ❌ | 请求本身错误，切了必重蹈覆辙，直接透传 |

### 5. 流式边界

以「是否已拿到可透传字节流」为分界：`forwardStream` 在连接建立前失败（抛异常）→ 按失败分类切换下一渠道；`StreamResult` 建立、字节开始透传后不再 failover（响应已发出，中断交由流监督层）。

### 6. 队列耗尽（全部渠道失败）

- **HTTP 错误**：透传最后一个被尝试渠道的状态码 + 错误体（OpenAI 形状原样透传，延续 ADR-0007/0008 不包装原则）；
- **网络异常**（无上游状态码）：映射 **502 + OpenAI 形状错误体** `{"error":{"message":"...","type":"upstream_error","code":"upstream_unavailable"}}`；
- 流式连接失败同样包装为 `ForwardResult{statusCode, raw, body}` 放进 `DispatchOutcome.unary`（OpenAI 协议下流式请求在连接阶段收到非 2xx JSON 错误体是标准行为）；**`DispatchOutcome` 不新增态**；
- `errorMessage` 汇总完整尝试序列（如 `all channels failed: [deepseek-001: HTTP 500, openai-002: connect timeout]`）；每次失败尝试 `log.warn(channelId, type, model, reason, elapsed)`，命中保持现有 `log.info`。

### 7. 本期边界（明确不做）

- 不做**同渠道重试**（429 后等待重试、同一渠道重试 N 次）；
- 不做**熔断/半开**；`lastTestOk`（管理面连通性测试结果）不参与运行时选择；
- 不做 429 `Retry-After` 等待；
- 模型映射数组（ModelMapping 多目标）保持等权随机（ADR-0007 Q8），本期不权重化——渠道级 weight 与映射级采样是两层，互不混用。

## Considered Options

- **全部内联进 ChannelDispatchServiceImpl**：否决。调度算法与编排职责混杂，算法不可单测；用户定位调度为无状态纯函数。
- **每次失败后重新轮盘赌（可放回）**：否决（用户修正）。可能重复选中已失败的渠道；正确语义是请求开始时一次性构建完整有序队列、每渠道最多尝试一次，尝试顺序符合权重分布。
- **同渠道重试 / 熔断 / lastTestOk 参与选择**：否决。本期只做跨渠道故障转移，避免范围蔓延。
- **DispatchOutcome 新增 failed 态**：否决。`ForwardResult` 已能承载失败错误体（statusCode/raw/body），流式连接阶段非 2xx JSON 错误体是标准行为，无需新态。
- **weight null/≤0 视为 1 或直接排除**：否决。视为 0 语义可预期（不参与分摊、保留兜底），总权重为 0 时退化原序，老数据无权重时行为可预期。

## Consequences

- 新增 `domain/channel/service/selector/IChannelSelector.java`、`domain/channel/service/selector/impl/ChannelSelectorImpl.java`；改造 `ChannelDispatchServiceImpl.invoke()`；
- GLOSSARY 新增 `ChannelSelector` 术语；`IDispatchInvoker` 的 _Avoid_ 移除 "dispatcher"（保留位已落位）；
- ADR-0009 §3 步骤 2/4/5 相关表述被取代（见 0009 Update 注记）；
- 测试：`ChannelSelectorImpl` 纯 JUnit 单测（app/src/test 按被测包路径建包：过滤、分组、轮盘赌概率分布多次统计、weight 边界/退化）；`ChannelDispatchServiceTest` 集成测试 `@SpringBootTest` + MockWebServer 模拟双上游（500→200 failover、同 priority 权重分布、流式连接失败切换）；
- 日志可观测性提升：每次失败尝试一条 warn + 最终 errorMessage 汇总，排障可追溯完整尝试序列。
