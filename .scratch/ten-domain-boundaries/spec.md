# Spec: 十域划分与领域边界

Status: ready-for-agent
Slug: ten-domain-boundaries
Created: 2026-10-20
决策依据: ADR-0002（十域划分、identity/account 边界、proxy 域范围；原"协议转换在 codec"条目已被 ADR-0006 取代，路由计划下沉已被 ADR-0009 取代）；术语见根目录 GLOSSARY.md

---

## Problem Statement

Java 版 llm-gateway 需要承接 Rust WaLiAPI 的完整功能面，初期必须先把领域边界钉死：哪些业务归哪个域、每个域的职责范围、哪些跨域决策放在哪一层，避免后续开发时出现逻辑跨域乱放、职责不清。本 spec 固化十域划分、关键边界（identity vs account、quota 归属、proxy 域范围），作为后续所有域实现的划分依据。

## Solution

领域层固定为十域：proxy / channel / identity / security / audit / account / settings / knowledge / wiki / mcp，忠实映射 WaLiAPI 的功能划分，不额外膨胀域。每域保持同构结构：model（实体/值对象/聚合）+ service（域服务）+ adapter（端口/仓储）。quota 不独立成域，按归属拆分：调用方密钥配额归 identity 域，上游第三方账号配额归 account 域。proxy 域负责转发主链路的外层编排（入站解析、鉴权调用、流监督、结果绑定），路由与渠道分发已按 ADR-0009 下沉到 channel 域。

## User Stories

1. 作为开发者，我想领域层固定为十个域，每个域有独立目录与同构结构，以便业务代码按归属清晰落位。
2. 作为开发者，我想 channel 域负责上游渠道配置、连通测试、适配器注册与路由决策，以便所有"上游连接"相关知识内聚在一处。
3. 作为开发者，我想 identity 域负责调用方 API Key 的身份、鉴权、配额、模型/渠道白名单，以便所有"调用方凭证"相关逻辑集中。
4. 作为开发者，我想 account 域负责上游第三方账号（codex/gemini/grok/kimi）的登录、token 刷新、第三方账号配额，以便区分"调用方密钥"与"上游账号"两类身份。
5. 作为开发者，我想 proxy 域只负责转发主链路的外层编排（入站解析、消息 sanitize、调用鉴权/配额、调 IDispatchInvoker、把结果绑到 ResponseBodyEmitter），路由计划与渠道分发下沉到 channel 域，以便 ProxyCase 保持薄编排。
6. 作为开发者，我想 security 域负责安全规则、请求扫描、敏感信息脱敏、拦截决策，以便安全逻辑独立演进。
7. 作为开发者，我想 audit 域负责请求日志落库、查询、清理，所有转发结果与安全审计记录不可变留存。
8. 作为开发者，我想 settings 域负责系统配置、特性开关，以便全局参数集中管理。
9. 作为开发者，我想 knowledge 域负责知识库 RAG 相关决策，wiki 域负责知识库文档管理，mcp 域负责 MCP 工具接入，以便这些扩展能力独立成域不污染主链路。
10. 作为开发者，我想 quota 不独立成域：API Key 配额归 identity 域，第三方账号配额归 account 域，避免额外拆分。
11. 作为开发者，我想入站侧统一 OpenAI 协议，本期不做入站多协议检测，协议形状转换全部内聚在渠道适配器（ADR-0006），proxy 域不承担协议转换职责。
12. 作为开发者，我想渠道身份（channel_identity）相关知识归 channel 域，特性开关（feature_flags）归 settings 域，边界清晰不跨域引用。

## Implementation Decisions

- **十域固定清单**：proxy、channel、identity、security、audit、account、settings、knowledge、wiki、mcp；每域在 domain 与 case 模块下对应同名子包。
- **同构结构**：每域下统一 `model/`（实体/值对象/聚合根）、`service/`（无框架依赖的域服务）、`adapter/`（对外端口、仓储接口）。
- **identity vs account 边界**：identity = 调用方（下游 SDK 用户）的 API Key；account = 上游 LLM 提供方的第三方登录账号（auth_provider）；两者完全独立，不共享实体。
- **quota 归属**：不设独立 quota 域；API Key 的 quota_limit/quota_used 归 identity 域，第三方账号的额度归 account 域。
- **proxy 域范围（按 ADR-0009 修订）**：proxy 域只做外层编排——入站请求解析、消息 sanitize、identity 鉴权/配额校验、调用一次 `IDispatchInvoker`（channel 域门面）、把返回的 DispatchOutcome 绑定到 ResponseBodyEmitter 做字节透传。路由计划、渠道匹配、模型映射采样、适配器分发全部下沉到 channel 域的 ChannelDispatchService。
- **管理面命令分组暂缓**：WaLiAPI 的 110+ 个 Tauri command 后续管理面阶段再做 Controller 分组，本期先按域落基础能力。

## Testing Decisions

- **测试接缝**：复用项目唯一接缝——@SpringBootTest 真实容器连本地 MySQL 跑整链路。
- **测试位置**：所有测试放 app/src/test，按被测域包路径建包。
- **好测试标准**：验证跨域调用的外部行为（请求经过正确域的处理链路、鉴权失败返回错误、转发成功返回上游结果），不验证域内部文件结构。
- **本 spec 验证点**：十域目录结构创建完成，跨域依赖方向正确，没有把 channel 相关逻辑写进 proxy 域、没有把密钥逻辑写进 account 域。

## Out of Scope

- 管理面 110+ Tauri commands 的 Controller 分组方案（后续管理面阶段收敛）。
- 入站多协议支持（/v1/messages Claude 入站、Gemini 入站）——本期不做，留待后续 codec 决策。
- 加权选渠道、重试、流监督等高级 proxy 编排能力——后续在 proxy 域 case 层扩展。
- 各域具体业务逻辑实现——在各域独立 spec 中定义。

## Further Notes

- 术语以 GLOSSARY.md 为准，域命名与边界已钉死，后续不随意跨域放置逻辑。
- proxy 域后续扩展（流监督、重试、加权 key 选择）仍在 proxy 层编排，但转发本体统一调 `IDispatchInvoker`，不回退到直接操作适配器。
