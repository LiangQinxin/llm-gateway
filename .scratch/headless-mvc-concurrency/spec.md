# Spec: 纯 headless 服务形态与 MVC 并发方案

Status: ready-for-agent
Slug: headless-mvc-concurrency
Created: 2026-10-20
决策依据: ADR-0003（纯 headless 服务、Spring MVC + Tomcat 线程池、Java 17、拒绝 WebFlux/虚拟线程）；术语见根目录 GLOSSARY.md

---

## Problem Statement

Java 版 llm-gateway 需要明确运行形态：原 Rust/Tauri 项目是带桌面壳的应用，Java 版承接时要决定是否保留桌面窗口、是否托管前端 SPA、并发栈选型。这些决策直接影响项目技术栈、依赖体积、并发能力上限与开发模式，必须在启动期固化，避免中途换栈导致全链路重写。

## Solution

定位为纯 headless HTTP 服务：不做桌面壳、不托管前端 SPA，只暴露两组 REST API——数据面 `/v1/*`（OpenAI 兼容转发）、管理面 `/admin/api/**`（运维管理接口）。并发栈采用 Spring MVC + 传统 Tomcat 线程池，Java 17 LTS 不升级，SSE 流式响应用 ResponseBodyEmitter 透传，出站 HTTP 调用用 okhttp-sse。明确拒绝 WebFlux 全响应式与 Java 21 虚拟线程方案。SSE 长连接占平台线程的并发天花板为已接受取舍（个人/小团队网关量级足够）。

## User Stories

1. 作为开发者，我想项目是纯 headless Spring Boot 服务，启动后只暴露 HTTP 端口，不启动桌面窗口、不托管前端 SPA，以便部署简单、资源占用低。
2. 作为开发者，我想所有对外接口分两组前缀：数据面 `/v1/*`（OpenAI 兼容转发）、管理面 `/admin/api/**`（渠道/密钥/日志管理），以便路径清晰、鉴权边界明确。
3. 作为开发者，我想并发栈用 Spring MVC + Tomcat 线程池（Java 17），同步代码形态，以便开发调试简单、不引入响应式编程复杂度。
4. 作为开发者，我想服务端 SSE 响应用 ResponseBodyEmitter 做字节级透传，长流异步不阻塞 Tomcat 请求线程，以便流式转发正常工作。
5. 作为开发者，我想出站调用上游 LLM 用 okhttp 客户端，SSE 流式消费用 okhttp-sse，以便与现有适配器实现保持一致。
6. 作为开发者，管理面鉴权预留 Bearer token 方案（WALIAPI_ADMIN_TOKEN 环境变量），基础实现阶段暂不加鉴权过滤器，后续安全域阶段补。
7. 作为开发者，我想异步请求超时全局配置 `spring.mvc.async.request-timeout`（默认 600000ms），以便长流式转发不被容器默认超时截断。
8. 作为运维方，我想 SSE 长连接并发天花板是 Tomcat 默认 200 平台线程，满足个人/小团队量级，高并发需求未来可平滑升级到 Java 21 虚拟线程。

## Implementation Decisions

- **运行形态**：纯 headless Spring Boot Web 服务，仅依赖 spring-boot-starter-web（MVC），不引入 JavaFX/Swing 桌面组件，不打包 Tauri 壳，不内置静态资源托管。
- **接口分组**：
  - 数据面：`/v1/*`，OpenAI 兼容协议，不做统一 Response 包装，上游状态码与错误体原样透传；
  - 管理面：`/admin/api/**`，统一响应 `Response{code,info,data}`，基础阶段暂不鉴权。
- **并发栈**：Spring MVC 同步模型 + Tomcat 默认线程池（最大 200 线程）；流式接口统一返回 `ResponseBodyEmitter`，异步转发放到 Spring Boot 自动配置的 `applicationTaskExecutor` 线程池。
- **出站 HTTP**：统一 okhttp3 客户端，流式响应用 okhttp-sse 的 EventSource；不引入 WebClient（避免响应式栈依赖）。
- **Java 版本**：固定 Java 17 LTS，不升级到 21+（虚拟线程为后续迁移路径，本期不启用）。
- **明确拒绝方案**：
  - WebFlux/Netty 全响应式：Mono/Flux 全链路重写，调试与测试成本过高；
  - Java 21 虚拟线程：需升 JDK，本期不升级；未来提升并发首选路径是切 Java 21 开虚拟线程，而非中途换 WebFlux。
- **管理面鉴权**：基础阶段不加过滤器，后续安全域阶段接入 `WALIAPI_ADMIN_TOKEN` Bearer 校验。

## Testing Decisions

- **测试接缝**：复用项目唯一接缝——@SpringBootTest 起真实容器连本地 MySQL 跑整链路，流式测试用流式客户端逐块断言。
- **测试位置**：统一放 app/src/test，按被测 Controller/Case 包路径建包。
- **好测试标准**：验证接口外部行为——同步请求正常返回、流式请求逐块收到 SSE 字节、异步超时配置生效；不断言 Tomcat 线程数等内部实现。
- **本 spec 验证点**：应用启动无桌面窗口，仅暴露 HTTP 端口；MVC 同步栈下 ResponseBodyEmitter 异步转发正常；SSE 长连接不阻塞 Tomcat 工作线程。

## Out of Scope

- WebFlux 响应式栈迁移、虚拟线程启用——本期明确不做。
- 前端 SPA 托管、静态资源服务——不做，前端独立部署。
- 桌面客户端壳（Tauri/Electron）——Java 版定位纯后端服务。
- 管理面鉴权过滤器实现——留待安全域 spec。
- 连接池、线程池参数调优——用 Spring Boot 默认值，后续按需调整。

## Further Notes

- SSE 长连接占平台线程是已知取舍：个人/小团队网关量级（百级并发）完全够用；未来如需支撑万级并发，优先升 Java 21 开虚拟线程（代码形态不变，仅改 JDK 版本加一行配置），而非中途换 WebFlux。
- 异步超时默认 600000ms（10 分钟），覆盖最长的上游流式生成场景。
