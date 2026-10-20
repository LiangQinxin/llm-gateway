# Spec: 七模块骨架、case 编排与依赖方向

Status: ready-for-agent
Slug: module-skeleton
Created: 2026-10-20
决策依据: ADR-0001（七模块骨架、case 编排、依赖方向；原第 8 模块 protocol 条目已被 ADR-0006 撤销）；术语见根目录 GLOSSARY.md

---

## Problem Statement

Java 版 llm-gateway 从零承接 Rust/Tauri WaLiAPI（v0.3.6）的功能面，首先需要建立与项目长期演进对齐的代码分层骨架：模块划分要既贴合六边形架构与用例编排惯例，又不引入过重的编译期边界约束，避免后续业务开发时在模块依赖上反复返工。本 spec 固化项目启动初期的模块结构、层间职责与依赖方向，作为后续所有域、用例、适配器落地的结构底座。

## Solution

固定为 7 个 Maven 模块：types（常量/枚举/异常）→ domain（十域纯决策）→ case（<域>.<Xxx>Case 编排）→ trigger（薄 Controller + filter）← infrastructure（MyBatis/okhttp 等技术实现）；api（DTO/Response/context）作为支撑；app 为组合根。编排在 case 层完成，Controller 只做"解析请求 → 调 Case → 序列化响应"，产出协议无关 Result 对象。依赖方向按六边形约定走，但不通过 Maven 强制收紧边界，靠命名与 review 维护；不设独立 protocol 模块（ADR-0006 已撤销）。

## User Stories

1. 作为开发者，我想根 pom 聚合 7 个子模块，以便用统一的 Maven 生命周期构建整个项目。
2. 作为开发者，我想 types 模块集中放全局常量、枚举、业务异常与统一响应码，以便所有层共享基础类型。
3. 作为开发者，我想 domain 模块按十域组织（proxy/channel/identity/security/audit/account/settings/knowledge/wiki/mcp），每域同构 model/service/adapter 三层，以便领域决策与技术实现解耦。
4. 作为开发者，我想 case 模块按 `cn.qx.cases.<域>.<Xxx>Case` 命名编排组件（@Component），注入域服务/仓储端口，产出协议无关的 `<Xxx>Result`（@Data @Builder），以便用例逻辑与 HTTP/持久化细节隔离。
5. 作为开发者，我想 trigger 模块只放薄 Controller 与全局 filter，Controller 不写业务逻辑、不直接调仓储，只做参数解析与响应序列化，以便 Web 层极薄、用例逻辑可复用。
6. 作为开发者，我想 infrastructure 模块承载所有技术实现细节（MyBatis Mapper、okhttp 客户端、适配器实现），只实现 domain 定义的端口接口，以便技术选型可替换。
7. 作为开发者，我想 app 模块作为组合根（Spring Boot 启动入口、全局配置、数据源装配），聚合所有其他模块，以便单一可执行 jar 启动。
8. 作为开发者，我想 api 模块承载请求/响应 DTO、统一响应体与请求上下文对象，以便 Web 层与 case 层的对象契约清晰。
9. 作为开发者，依赖方向固定为 trigger → case → domain ← infrastructure，app 聚合启动；允许 trigger 直连 domain、domain 保留框架依赖，靠约定而非编译期检查维护边界，以便快速迭代不被严格分层拖慢。
10. 作为开发者，我想项目不设独立 protocol 模块，上游协议转换全部内聚在渠道适配器（ILlmAdaptor）内部，以便避免双轨维护、减少模块数量。
11. 作为开发者，我想每个模块都有清晰的包路径根（cn.qx.<模块>），以便新代码按归属快速找到位置。

## Implementation Decisions

- **模块清单**（7 个，对齐 ADR-0001）：
  - `types`：全局常量、枚举、AppException、ResponseCode，无业务依赖；
  - `domain`：十域纯领域决策，每域下 `model/`（实体/值对象/聚合）、`service/`（域服务）、`adapter/`（端口/仓储接口）；
  - `case`：按域组织用例编排，类名 `<Xxx>Case`，注入端口与域服务，输出 `<Xxx>Result`；
  - `trigger`：Spring MVC Controller + 全局异常处理器 + 过滤器；管理面前缀 `/admin/api/**`，数据面前缀 `/v1/**`；
  - `infrastructure`：MyBatis Mapper XML/Repository 实现、okhttp 客户端、渠道适配器实现，仅依赖 domain 端口接口；
  - `api`：对外 DTO、统一响应 `Response{code,info,data}`、请求上下文；
  - `app`：Spring Boot 启动类、application.yml、全局配置、数据源装配，作为组合根依赖上述全部模块。
- **不设第 8 个 protocol 模块**：原 ADR-0001 中独立协议编解码模块的决策已被 ADR-0006 撤销，SSE 透传与协议转换全部内聚到适配器与 proxy 执行编排。
- **依赖方向约定**：trigger → case → domain ← infrastructure；app 聚合所有模块启动；允许 domain 保留必要的框架依赖（okhttp、fastjson 等），不做编译期强制边界。
- **包命名**：根包统一 `cn.qx`，模块对应子包 `cn.qx.<模块名>`；十域在 domain/case 下按域名分子包。
- **Spring Bean 装配**：Case 层组件标注 @Component，域服务标注 @Service，仓储实现标注 @Repository，适配器按厂商命名 Bean 注入 map。

## Testing Decisions

- **测试接缝**：唯一接缝为 `@SpringBootTest` 起真实 Spring 容器连本地 MySQL，从 Case/Controller 层跑整链路；不手工装配、不 mock Bean，与项目测试约定一致。
- **测试位置**：统一放 `app/src/test`，按被测包路径建包；禁止放 domain 等模块的 test 目录。
- **好测试标准**：只验证外部行为（接口响应、业务结果、持久化效果），不断言内部分层结构；入参出参打 log + JSON。
- **本 spec 验证点**：所有模块能正常编译打包，Spring Boot 应用能正常启动，7 个模块在根 pom 中聚合正确。

## Out of Scope

- 具体业务功能实现（渠道管理、密钥、转发等）——后续各域 spec 覆盖。
- Maven 依赖收紧（通过 ArchUnit 等工具做编译期边界校验）——本期明确不做，靠约定维护。
- 模块化升级为 JPMS（Java 模块系统）——不做。
- 各域具体实体、端口、服务的业务逻辑——在各域独立 spec 中定义。

## Further Notes

- 本 spec 是项目最早的结构底座决策，后续所有功能 spec 均在此模块骨架内落地。
- 原 ADR-0001 中"第 8 模块 llm-gateway-protocol"条目已在 ADR-0006 中正式撤销，本 spec 同步不包含该模块。
- 边界维护方式：code review 时检查依赖方向，不引入 ArchUnit 等强制检查工具。
