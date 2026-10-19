# Spec: 三表基础实现（channels / api_keys / request_logs）

Status: ready-for-agent
Slug: three-table-baseline
Created: 2026-10-19
决策依据: ADR-0001 ~ ADR-0006；术语见根目录 GLOSSARY.md；实现蓝图见 docs/三表基础实现-设计计划.md

---

## Problem Statement

Java 版 llm-gateway 目前只有 7 个模块的 package-info 骨架与少量基础类（Response / AppException / ResponseCode / Constants），没有任何业务代码。本项目承接 Rust/Tauri WaLiAPI（v0.3.6）的功能面，必须先落地最基础的三张表（channels、api_keys、request_logs）的完整能力——渠道全生命周期管理与连通测试、密钥认证与配额控制、请求日志审计——作为后续 proxy 主链路、安全扫描、知识库等其余域的样板底座。

## Solution

按六边形分层（trigger→case→domain←infrastructure）+ 用例编排（Controller 极薄，编排在 Case 层），三张表分别落 channel / identity / audit 三域：domain 定义实体、仓储端口与域服务；infrastructure 用 MyBatis 实现仓储、用 okhttp 实现渠道连通探测；case 层提供协议无关编排；trigger 提供薄 Controller（统一前缀 /admin/api/**）；app 为组合根。时间与 ID 全 String（ISO-8601 带偏移 + UUID），JSON 列实体保持 String + 便捷解析方法。全部能力通过 @SpringBootTest 起真实容器连本地 MySQL 跑整链路验证。

## User Stories

### 渠道管理（channel 域）

1. 作为网关管理员，我想创建渠道（名称、类型、base_url、上游密钥、模型列表、优先级、权重、config、model_mapping），以便接入上游 LLM 提供方。
2. 作为网关管理员，我想列出全部渠道，以便集中管理上游配置。
3. 作为网关管理员，我想查看单个渠道详情，以便核对配置内容。
4. 作为网关管理员，我想修改渠道的全部配置，以便调整上游连接。
5. 作为网关管理员，我想启用/停用单个渠道（status 切换），以便临时摘除故障上游而不删除配置。
6. 作为网关管理员，我想删除不再使用的渠道，以便清理配置。
7. 作为路由系统（proxy 阶段调用），我想只查到 status=1 的渠道并按 priority DESC、weight DESC 排序，以便正确选择上游候选。
8. 作为网关管理员，我想对渠道发起手动连通性测试，以便在管理端看到渠道是否可用。
9. 连通测试记录 last_test_at 与 last_test_ok（1=成功 0=失败，null=未测试），以便渠道卡片展示健康状态。
10. 连通测试采用 legacy 单协议语义：type 决定协议（openai/anthropic/custom）、base_url 即上游基础地址、config 为额外配置 JSON、model_mapping 为模型名映射 JSON（key=请求模型名，value=上游模型名）。
11. 连通探测矩阵：openai/custom → GET {base_url}/models（Bearer 鉴权头）；anthropic → GET {base_url}/v1/models（x-api-key + anthropic-version 头）；2xx 即判通。

### 密钥管理（identity 域）

12. 作为网关管理员，我想创建密钥（名称、可选自定义 key；留空则自动生成 sk-waliapi-<uuid>），以便分发调用方凭证。
13. 作为网关管理员，我想密钥全局唯一（key 列唯一约束），重复创建返回业务错误，以便避免凭证冲突。
14. 作为网关管理员，我想列出/查看/修改/删除密钥，以便管理调用方凭证。
15. 作为网关管理员，我想启用/停用密钥，以便临时吊销调用方凭证而不删除。
16. 作为网关，我想按 key 精确匹配且 status=1 且未过期（expires_at 为空或晚于当前时间）认证请求，以便拒绝非法、禁用、过期凭证。
17. 作为网关，我想按 allowed_models / allowed_channels（JSON 数组，空数组=全部允许）做模型级与渠道级白名单鉴权，以便限制单个密钥的可用范围。
18. 作为网关管理员，我想设置 quota_limit 额度上限（0=不限），以便控制调用方成本。
19. 作为网关，我想在请求完成后累加 quota_used，并在累加时封顶保护（quota_limit>0 时不超过上限；=0 不限），以便防止超额。
20. 作为网关管理员，我想设置 expires_at 过期时间（为空=永不过期），以便密钥到期自动失效。

### 请求日志与审计（audit 域）

21. 作为网关，每次转发请求落一条审计记录，seq 自增保证记录顺序，日志只增不改，以便保留完整审计历史。
22. 作为网关管理员，我想按 model / api_key / channel / status_code / created_at 区间筛选并分页查询日志，以便排查问题。
23. 作为网关管理员，我想查看单条日志详情（含 request_body 与 response_choices），以便复盘请求与响应。
24. 作为网关管理员，我想按时间批量清理 created_at 之前的日志、以及一键清空全部日志，以便控制存储成本。
25. 作为审计方，日志记录 Token 用量（prompt_tokens、completion_tokens、total_tokens、duration_ms），以便核算用量。
26. 作为安全审计方，日志记录风险字段（risk_level: Clean/Low/Medium/High/Critical、risk_score、risk_summary、security_action: Allow/Sanitize/Block、sanitized、blocked_reason），以便追溯安全处理结果。
27. 作为运维方，日志记录 error_message、trace_id、client_ip、stream/is_retry 标记与 upstream_model，以便链路追踪与问题定位。

## Implementation Decisions

- **模块**：沿用 7 模块（types / domain / case / app / api / trigger / infrastructure）；不设独立 protocol 模块（ADR-0006：上游协议转换内聚于渠道适配器 ILlmAdaptor 端口，本期入站侧统一 OpenAI 协议）。
- **域归属**：channels → channel 域；api_keys → identity 域；request_logs → audit 域。每域同构 model（entity/valobj）+ service + adapter（port/repository）。
- **出站端口**：连通测试走 channel 域定义的 IChannelProbePort，infrastructure 用 okhttp 实现；okhttp 不进入 domain。
- **仓储关键行为**：认证查询条件（key 精确 + status=1 + expires_at 为空或晚于当前时间）；配额递增由 SQL 原子封顶（quota_limit=0 走不限分支）；日志清理为 DELETE WHERE created_at < 阈值 与全表清空；路由查询为 status=1 ORDER BY priority DESC, weight DESC。
- **Schema**：以 docs/mysql/sql/llm_gateway.sql 为唯一来源（channels 15 列、api_keys 11 列、request_logs 29 列，含 uk_key、uk_seq、idx_created_at/idx_api_key/idx_channel）；WaLiAPI Rust 扩展字段（T02 协议身份列、denied_*、T09 观测列）本期不引入，后续 proxy 阶段按需 ALTER。
- **时间/ID 建模**：时间列实体与 PO 均为 String（ISO-8601 带偏移，与种子数据同格式；字符串字典序=时间序，BETWEEN 与 ORDER BY 直接命中索引）；ID 为 UUID 36 字符（varchar(64) 富余）。
- **JSON 列建模**：models/config/model_mapping/allowed_models/allowed_channels/request_body/response_choices 实体保持 String，提供便捷解析方法（如 activeModelMapping() 返回映射 Map）；PO 与实体同构，免转换层。
- **API 契约**：管理面 REST 统一前缀 /admin/api/**，三组资源：channels（含 toggle、test、enabled 查询）、keys（含 toggle）、logs（分页、详情、按时间清理、清空）；统一响应 Response{code, info, data}；业务异常抛 AppException，由 trigger 全局 @RestControllerAdvice 映射为 HTTP。
- **管理面鉴权**：本期不加鉴权过滤器（Q6-a），Bearer token 方案留待安全域阶段。
- **测试前置改动**：app pom 的 surefire 当前 skipTests=true，需改为 false 测试才会执行。

## Testing Decisions

- **测试接缝（单一接缝）**：@SpringBootTest 起真实 Spring 容器连本地 MySQL，从 Case 层（经 Controller/Case → 域服务 → 仓储 → MySQL）跑整链路；不 mock 任何 Bean、不手工装配——与已收敛测试策略（ADR-0005）一致，全项目仅这一个接缝。
- **测试位置**：统一放 app/src/test，按被测包路径建包（被测 cn.qx.cases.channel.ChannelCase → app/src/test/java/cn/qx/cases/channel/），禁止放 domain 等模块 test 目录。
- **好测试标准**：只验证外部行为（CRUD 结果、认证通过/拒绝、白名单放行/拦截、配额封顶效果、清理效果、seq 递增），不断言内部实现细节；每个用例入参出参 log + JSON 打印。
- **覆盖边界**：每域正常流 + 边界（重复 key 唯一冲突、禁用/过期密钥认证失败、白名单空数组=全放行、配额超上限不再累加、日志 seq 只增不改、按时间清理只删阈值之前）。
- **先例**：本 spec 是全新项目首个实现，本测试即后续各域测试的样板（app/src/test 下 cn/qx/test/ApiTest.java 仅为占位）。

## Out of Scope

- 管理面 110+ 个 WaLiAPI Tauri commands 的分组映射（原 Q5 搁置；本期仅 channels/keys/logs 三个 Controller）。
- 管理面鉴权（Bearer token / CSRF / 会话管理）。
- proxy 主链路（/v1/* 数据面转发、dispatcher、流式 SSE 下发、ILlmGateway 端口真实实现）。
- 安全扫描域（security rules / scanner / redact）。
- account / settings / knowledge / wiki / mcp 其余域及其表。
- Redis / 语义缓存、OTLP 导出、健康探针、事件桥。
- 统计聚合（dashboard stats / model stats / token 趋势）。
- 并发栈升级（ADR-0003：MVC + Tomcat 线程池、Java 17；WebFlux / 虚拟线程不做）。
- Web 管理面板 SPA / 前端托管。

## Further Notes

- 实现顺序：① app pom surefire 开关 + application.yml 数据源配置 → ② domain（枚举/实体/仓储端口/域服务）→ ③ infrastructure（PO/Mapper XML/RepositoryImpl/OkHttpChannelProbe）→ ④ case（三域 Case + Result）→ ⑤ trigger（三 Controller + 全局异常映射）→ ⑥ 三域 CaseTest 跑通整链路。
- 端口清单、Mapper SQL 全文、测试用例清单见 docs/三表基础实现-设计计划.md（实现时以此为准）。
