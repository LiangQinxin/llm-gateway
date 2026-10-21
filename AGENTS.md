# AGENTS.md

## Agent 技能

### 问题跟踪器

问题与规格以 markdown 文件形式存放在 `.scratch/<feature-slug>/` 下。参见 `docs/agents/issue-tracker.md`。

### Triage 标签

五个标准角色标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。参见 `docs/agents/triage-labels.md`。

### 领域文档

单上下文：根目录一个 `GLOSSARY.md` + `docs/adr/` 存放全部决策。参见 `docs/agents/domain.md`。

## 架构与约定

Java 版 LLM 网关，承接 Rust/Tauri WaLiAPI（v0.3.6）。六边形分层 + 用例编排，对齐 llm-api 惯例。核心决策见 `docs/adr/0001~0005`，术语见根目录 `GLOSSARY.md`。

- **模块**：`types`（常量/枚举/异常）→ `domain`（十域纯决策：proxy/channel/identity/security/audit/account/settings/knowledge/wiki/mcp）→ `case`（`cn.qx.cases.<域>.<Xxx>Case` 编排）→ `trigger`（薄 Controller + filter）← `infrastructure`（MyBatis/okhttp 实现）；`api`（DTO/Response/context）；`app` 组合根。上游协议转换内聚于渠道适配器（channel 域 `ILlmAdaptor` 端口 + infrastructure 实现，见 ADR-0006），不设独立 protocol 模块；
- **依赖方向**：trigger→case→domain←infrastructure；app 聚合启动；依赖沿用 llm-api 现状**不收紧**（边界靠约定）；
- **服务与接口约定**：case 编排类（`cn.qx.cases.<域>.<Xxx>Case`）与领域服务（`domain/<域>/service`）**必须定义接口**（I 前缀：`I<Xxx>Case` / `I<Xxx>Service`）。接口位置：签名含 servlet/spring-web 类型的 case 接口就近放 case 层同包（如 `cases.proxy.IProxyCase`）；其余 case 接口放 **api 根包**（`IApiKeyCase`/`IChannelCase`/`IRequestLogCase`）；领域服务接口放 domain 同包 `service` 下，实现类 `<Xxx>ServiceImpl`（如 `IChannelDispatchService` + `ChannelDispatchServiceImpl`）。trigger 一律注入接口类型，禁止注入具体实现类；
- **输出对象（DTO）分层约定**：case 编排产出、直接交给 trigger 的协议无关输出对象（Result/DTO）放 **api/dto/<域>** 子包（如 `ProxyResult` → `api/dto/proxy`、`RequestLogPageResult` → `api/dto/audit`）；端口契约值对象与实体（`TestResult`/`StreamResult`/`ForwardResult`/`DispatchOutcome`/`ProxyRequest`、`Channel`/`ApiKey`/`RequestLog` 等）留在 domain——domain 端口不得反向依赖 api。api 模块因接口/输出对象签名引用领域类型而依赖 domain（无环，依赖不收紧）；
- **schema 唯一来源**：`docs/mysql/sql/llm_gateway.sql`（基础版；Rust 扩展字段按需 ALTER，见 ADR-0004）；
- **存储**：MySQL + MyBatis；Redis 仅预留位。**时间/ID 全 String**（ISO-8601 带偏移 + UUID），JSON 列实体 String + 便捷解析；
- **运行形态**：纯 headless Spring Boot 服务（MVC + Tomcat 线程池，Java 17）；数据面 `/v1/*`、管理面 `/admin/api/**`；
- **测试约定**：`@SpringBootTest` 起真实容器连本地 MySQL 跑整链路，不做手工装配/不过度 mock，入参出参 log+JSON；测试统一放 `app/src/test` 按被测包路径建包（如被测 `cn.qx.cases.channel.ChannelCase` → `app/src/test/java/cn/qx/cases/channel/`），禁止放 domain 等模块 test 目录；注意 app pom surefire 需 `skipTests=false` 才能跑。
