# ADR-0003: 纯 headless 服务形态与 MVC 并发方案

Java 版定位为纯 headless Spring Boot 服务（不做桌面壳、不托管 SPA），管理面转 REST API；并发采用 Spring MVC + 传统 Tomcat 线程池（Java 17 不升级），SSE 长连接的并发天花板为已知取舍。

## Decision

- **运行形态**：纯 headless HTTP 服务，`/v1/*` 数据面 + `/admin/api/**` 管理面；管理面鉴权为 Bearer token（环境变量配置），基础实现阶段暂不加鉴权过滤器（Q6-a，安全域阶段补）；
- **并发方案**：Spring MVC + Tomcat 线程池（Java 17），服务端 SSE 用 SseEmitter/StreamingResponseBody，出站消费用 okhttp-sse（`EventSource`）→ 首帧校验/空闲超时语义照搬原项目实现；
- **明确拒绝**：WebFlux 全响应式（运行时对标 tokio 但代码形态全变、重写与调试成本高）；虚拟线程（需升 Java 21，pom 现为 17）。

## Considered Options

- WebFlux（Netty 事件循环，最接近 tokio 运行时；代价：Mono/Flux 订阅链重写全部链路，@SpringBootTest/MDC/流式断言均需特殊处理）；
- MVC + 虚拟线程（Java 21：同步代码形态不变、SSE 长连接不再占平台线程；代价：JDK 升级）。

## Consequences

SSE 长连接占平台线程（Tomcat 默认 200），高并发连接数是硬天花板——个人/小团队网关量级接受。未来如需提升并发，首选迁移路径为 Java 21 虚拟线程（代码形态不变，仅改 JDK 与一行配置），而非中途换 WebFlux。
