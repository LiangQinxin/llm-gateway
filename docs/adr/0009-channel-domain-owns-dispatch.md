# ADR-0009: 渠道域承担路由决策与转发编排，IDispatchInvoker 为对 ProxyCase 的门面

把 ADR-0007 划在 proxy 域的 route_plan（按 model 全查启用渠道、匹配、ModelMapping 采样、按 channel.type 路由到适配器、执行 forward/forwardStream、组装结果）整段下沉到 channel 域。ProxyCase 只做：入站解析 → sanitize → identity 鉴权/配额 → 调一次 `IDispatchInvoker` → 把结果绑到 `ResponseBodyEmitter` 透传。

Status: accepted（取代 ADR-0007 中「ChannelSelection 属 proxy 域 / ProxyCase 编排水路计划」与 ADR-0002 中 proxy 域「路由计划、渠道分发」条目；上游协议转换仍归 ILlmAdaptor，ADR-0006 不变；字节级透传与 emitter 绑定仍按 ADR-0008，不变）。

> **Update (ADR-0012)**：§3 步骤 2/4/5 的「内存找第一个命中」与「采样在路由阶段一次预烘焙」已被 ADR-0012 取代——改为 ChannelSelector 构建 failover 队列、按序尝试、每次尝试前重新烘焙 body.model；失败按分类切换下一渠道。

## Decision

### 1. 门面接口 `IDispatchInvoker`（channel 域 port）

对 ProxyCase 暴露的唯一转发入口，单方法：

```java
public interface IDispatchInvoker {
    DispatchOutcome invoke(ProxyRequest request);
}
```

ProxyCase 不认识适配器、不认识 map、不认识渠道查询。接口只吃 `ProxyRequest{model, body:JSONObject, stream}`（已 sanitize）。

### 2. 返回值 `DispatchOutcome`（channel 域 valobj）

字节级中立载体，一次表达三种结果：

```java
public class DispatchOutcome {
    private boolean routed;          // false = 无渠道支持该 model → 404
    private String errorMessage;     // routed=false 时给 OpenAI 错误体用
    private ForwardResult unary;     // stream=false 非空
    private StreamResult streaming;  // stream=true 非空（含 OkHttp Call cancel）
}
```

- **字节级中立**：`ForwardResult.raw:String` 与 `StreamResult.body:InputStream` 不绑协议；`body:JSONObject` 与 `usage:TokenUsage` 只是 OpenAI 形状下给日志/计费用的便捷解析。
- **入站多协议口子**：将来加 `/v1/messages`（Claude 入站）时，新增的 `MessagesCase` 负责「Claude 请求 → OpenAI 格式 → 调同一个 `IDispatchInvoker` → OpenAI 响应 → 转回 Claude 形状」。invoker 本身不动，case 层 codec 做双向转换（另立 ADR，对应 ADR-0006 留的口子）。

### 3. 唯一实现 = 路由器 `ChannelDispatchService`（channel 域 service）

构造注入 `IChannelRepository` + `Map<String, ILlmAdaptor>`（Spring 按 Bean name 注入全部厂商适配器）。`invoke(req)` 内部串联：

1. `channels = repo.listEnabledOrdered()`（`status=1 ORDER BY priority DESC, weight DESC`，不分页，匹配与采样在内存）；
2. 内存找第一个命中：`c.activeModels().contains(req.model) || c.activeModelMapping().containsKey(req.model)`；
3. 未命中 → `DispatchOutcome.notFound(req.model)`（routed=false）；
4. 命中 → `upstreamModel = new ModelMapping(hit.activeModelMapping()).resolve(req.model, ThreadLocalRandom.current())`；
5. 烘焙：`req.getBody().put("model", upstreamModel)`（ADR-0007 Q8=②：采样在路由阶段一次预烘焙，适配器不重采样）；
6. `cfg = hit.toChannelConfig()`；
7. `adaptor = map.get(hit.getType())`；
8. 按 `req.stream` 分支：`adaptor.forward(req, cfg)` 或 `adaptor.forwardStream(req, cfg)`，包进 `DispatchOutcome`。

**`@PostConstruct` 启动自检**：遍历 `listEnabledOrdered()` 的所有 channel.type，断言每个 type 都能在 map 里找到对应 Bean；启动期配置漂移直接炸出来，运行时不再出现 502 channel_type_unavailable。

### 4. 废弃 `IChannelAdaptorRegistry`

现有 `getAdaptor(channelType)` 是 `Map<String, ILlmAdaptor>.get` 的薄包装，端口删除。管理面连通性测试（`ChannelCase`）改为直接注入 `Map<String, ILlmAdaptor>`。

### 5. 仓储补方法

`IChannelRepository` 新增 `List<Channel> listEnabledOrdered()`（SQL 语义：`WHERE status=1 ORDER BY priority DESC, weight DESC`）。

### 6. 失败 HTTP 形态（由 ProxyCase 翻译成 OpenAI 风格错误体）

- **model 未命中**（routed=false）：HTTP 404，体 `{"error":{"message":"The model 'xxx' does not exist or you don't have access to it","type":"invalid_request_error","code":"model_not_found"}}`；
- **channel.type 无 adaptor**：启动自检已拦住；运行时若仍到这一层，抛 `IllegalStateException`（真 bug，打堆栈优于写 502 JSON）。

### 7. 其他不变

- sanitize（空 content 的 assistant 消息清洗）留在 ProxyCase 入站侧，与渠道无关；命中原生端点时由适配器按 ADR-0011 §4 复用原始请求字节；
- `StreamResult.cancel`（OkHttp Call）原样透传：adaptor → DispatchOutcome.streaming → ProxyCase 挂到 `emitter.onCompletion/onError`；
- `ThreadLocalRandom.current()` 作为数组映射采样的随机源（无锁、无线程竞争、无创建开销）；
- 流式/非流式统一 `ResponseBodyEmitter`、字节级透传、非 2xx 错误体透传——全部沿用 ADR-0008。

### 8. 后续协议扩展

ADR-0011 在 `ProxyRequest` 上扩展入站协议标签与数据面路径，用于适配器选择有序原生端点；channel 域仍不解析 body，本 ADR 的「路由决策归 channel 域、ProxyCase 只编排」边界不变。

## Considered Options

- **route_plan 留 proxy 域（ADR-0007 原方案）**：否决。门面化后 ProxyCase 变薄，渠道相关知识不外泄；用户拍板「调度 + model 重写 + 适配器路由 + 响应组装全部在渠道领域完成」。
- **每厂商一个 `IDispatchInvoker` Bean、map key=渠道类型**：否决。IDispatchInvoker 是对 ProxyCase 的门面，应该是单例；按 type 分发由路由器内部 `Map<String, ILlmAdaptor>` 完成。
- **统一 `invoke(req, cfg, upstreamModel)` 单方法签名**：否决。烘焙在路由器一次完成，适配器只看到最终 body.model，invoker 端口不暴露 upstreamModel；流式分支由路由器按 `req.stream` 决定，不污染端口签名。
- **返回 Object + instanceof 分支**：否决。ProxyCase 拿到返回值还要再判断类型，不如统一 outcome 一次把三种结果表达完。
- **model 未命中抛异常**：否决。这是下游客户端请求了不支持的 model，是常见业务分支（OpenAI SDK 会收到 404），不是异常。

## Consequences

- proxy 域 service 目录退化为空壳；后续流监督、重试、加权选 key 等扩展仍在 proxy 域 case 层编排，但转发本体调 `IDispatchInvoker`。
- `IChannelAdaptorRegistry` 删除；`ChannelCase`（管理面连通性测试）同步改造为直接注入 `Map<String, ILlmAdaptor>`。
- 新增 domain 文件：`port/IDispatchInvoker.java`、`model/valobj/DispatchOutcome.java`、`service/ChannelDispatchService.java`；`IChannelRepository` 加一个方法。
- GLOSSARY 同步修订「Proxy 域」范围，新增 `IDispatchInvoker` 与 `DispatchResult` 术语（已落）。
