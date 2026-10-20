# Spec: 渠道域承担路由决策与 IDispatchInvoker 转发门面

Status: ready-for-agent
Slug: channel-dispatch-invoker
Created: 2026-10-20
决策依据: ADR-0009（路由决策下沉 channel 域，IDispatchInvoker 为对 ProxyCase 的唯一转发门面；取代 ADR-0007 中 ChannelSelector 属 proxy 域的旧决策；协议转换仍归 ILlmAdaptor（ADR-0006）、字节透传仍按 ADR-0008，不变）；术语见根目录 GLOSSARY.md

---

## Problem Statement

原转发链路设计（ADR-0007）把路由计划（全查启用渠道、按 model 匹配、模型映射采样、按 channel.type 分发到适配器）划在 proxy 域，导致 ProxyCase 承担了过多渠道相关知识，proxy 域与 channel 域边界模糊。需要把路由与分发逻辑整体下沉到 channel 域，给 ProxyCase 暴露一个薄门面接口，让 ProxyCase 回归纯外层编排，渠道相关知识全部内聚。

## Solution

在 channel 域新增 `IDispatchInvoker` 端口（门面），作为对 ProxyCase 暴露的唯一转发入口，单方法 `DispatchOutcome invoke(ProxyRequest req)`。唯一实现是 `ChannelDispatchService`（channel 域 service），内部串联：全查启用渠道 → 按 model 内存匹配第一个命中 → ModelMapping 采样得到 upstreamModel → 预烘焙进请求体 → 按 channel.type 从适配器 map 取对应适配器 → 按 stream 分支执行 forward/forwardStream，返回字节中立的 `DispatchOutcome`。ProxyCase 只做入站解析、sanitize、鉴权配额调用，然后调一次 invoker 拿到结果，绑定到 ResponseBodyEmitter 透传。同时废弃原 `IChannelAdaptorRegistry`，直接用 Spring 注入的 `Map<String, ILlmAdaptor>`。

## User Stories

1. 作为开发者，我想 channel 域对外暴露唯一转发门面 `IDispatchInvoker`，ProxyCase 不直接接触仓储、不直接查渠道、不感知适配器 map，以便 ProxyCase 变薄、渠道知识不外泄。
2. 作为开发者，我想门面接口只吃 `ProxyRequest{model, body:JSONObject, stream}`（已 sanitize），不暴露仓储、适配器等内部依赖，以便端口极简。
3. 作为开发者，我想返回值 `DispatchOutcome` 一次表达三种结果：未命中（routed=false + errorMessage）、非流式结果（unary ForwardResult）、流式结果（streaming StreamResult），以便 ProxyCase 拿到后直接分支处理，不用再 instanceof 判断。
4. 作为开发者，我想 DispatchOutcome 是字节中立的：ForwardResult.raw 是上游原始字符串、StreamResult.body 是上游原始字节流，不绑定 OpenAI 协议形状，以便未来加 Claude 入站 case 时复用同一个 invoker。
5. 作为开发者，我想 ChannelDispatchService 内部按顺序执行：全查启用渠道（status=1 ORDER BY priority DESC, weight DESC）→ 内存找第一个命中（models 包含或 model_mapping key 存在）→ ModelMapping 数组采样得 upstreamModel → 烘焙进 body.model → 取对应适配器 → 按 stream 分支调用 forward/forwardStream。
6. 作为开发者，我想 channel.type 无对应适配器的问题在启动期自检阶段就暴露：@PostConstruct 遍历所有启用渠道的 type，断言 map 里都有对应 Bean，启动直接失败，避免运行时出现 502 channel_type_unavailable。
7. 作为开发者，我想废弃旧的 `IChannelAdaptorRegistry`，管理面连通性测试的 ChannelCase 直接注入 `Map<String, ILlmAdaptor>` 按 type 取适配器，以便删掉薄包装类。
8. 作为开发者，我想 IChannelRepository 新增 `listEnabledOrdered()` 方法，SQL 语义为 `WHERE status=1 ORDER BY priority DESC, weight DESC` 全查不分页，以便匹配与采样在内存完成。
9. 作为开发者，model 未命中时 invoker 返回 routed=false，由 ProxyCase 翻译成 OpenAI 风格 404 错误体，不抛异常，以便未命中是正常业务分支而非系统错误。
10. 作为开发者，数组模型映射采样用 `ThreadLocalRandom.current()` 作为随机源，无锁无竞争，以便高并发下采样无性能瓶颈。
11. 作为开发者，StreamResult 的 cancel 句柄（OkHttp Call）原样透传：适配器 → DispatchOutcome.streaming → ProxyCase 挂到 emitter.onCompletion/onError，客户端断连时取消上游调用。

## Implementation Decisions

- **新增 port（channel 域 adapter 包）**：
  ```java
  public interface IDispatchInvoker {
      DispatchOutcome invoke(ProxyRequest request);
  }
  ```
- **新增值对象（channel 域 model/valobj 包）**：
  ```java
  public class DispatchOutcome {
      private boolean routed;
      private String errorMessage;
      private ForwardResult unary;
      private StreamResult streaming;
      // 静态工厂：notFound(model) / unary(result) / streaming(result)
  }
  ```
- **新增域服务（channel 域 service 包）**：`ChannelDispatchService`，构造注入 `IChannelRepository` + `Map<String, ILlmAdaptor>`；invoke 内部串联全查→匹配→采样→烘焙→分发；@PostConstruct 做启动自检。
- **仓储扩展**：`IChannelRepository` 新增 `List<Channel> listEnabledOrdered()`；infrastructure 层对应 Mapper SQL 实现。
- **删除旧端口**：`IChannelAdaptorRegistry` 类删除，`ChannelCase`（管理面连通测试）改造为直接注入 `Map<String, ILlmAdaptor>`。
- **失败 HTTP 形态**：routed=false 时 ProxyCase 返回 HTTP 404，错误体为 OpenAI 风格 `{"error":{"message":"The model 'xxx' does not exist or you don't have access to it","type":"invalid_request_error","code":"model_not_found"}}`；channel.type 无适配器在启动自检已拦住，运行时若触发直接抛 IllegalStateException。
- **其他不变**：sanitize 留 ProxyCase 入站侧、字节透传/非 2xx 错误体/ResponseBodyEmitter 机制全部沿用 ADR-0008，适配器协议转换逻辑不变。

## Testing Decisions

- **测试接缝**：复用项目唯一接缝——@SpringBootTest 起真实容器连本地 MySQL + 真实上游，从 ChannelDispatchService 层跑整链路。
- **测试位置**：测试放 app/src/test/java/cn/qx/domain/channel/ 对应包路径下。
- **好测试标准**：只验证外部行为——invoker 正确返回 routed=false（未命中）、正确选中命中渠道、正确采样并烘焙 upstreamModel、正确分发到对应适配器、流式返回 StreamResult 含 cancel 句柄、启动自检对缺失适配器类型报错；不断言内部私有方法。
- **覆盖边界**：渠道未命中 404、字符串映射命中、数组映射采样、启用渠道按优先级权重排序、流式/非流式分支正确返回、启动自检对未注册适配器类型失败。
- **先例**：ProxyCaseTest、DeepSeekAdaptorTest 为同缝既有样板。

## Out of Scope

- 加权负载均衡路由（多渠道按权重分流）——本期取第一个命中，后续扩展。
- 重试、熔断、流监督逻辑——后续在 proxy 域编排层加，不进 ChannelDispatchService。
- 入站多协议转换（Claude/Gemini 请求转 OpenAI 后再调 invoker）——后续 MessagesCase 实现，invoker 本身不动。
- 渠道健康检查、自动摘除故障渠道——后续扩展。

## Further Notes

- 本 ADR 是对 ADR-0007 转发链路设计的边界修正：原 proxy 域下的 ChannelSelector 代码迁移到 channel 域 ChannelDispatchService，proxy 域 service 目录退化为空壳，后续 proxy 层扩展只在 case 层编排，不再持有路由知识。
- GLOSSARY 已同步修订：proxy 域范围移除路由计划，新增 IDispatchInvoker 与 DispatchOutcome 术语。
- 迁移后 proxy-forward-link 旧 spec 中的 ChannelSelection 类废弃，改为 DispatchOutcome。
