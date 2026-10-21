# ADR-0010: 数据面入站认证与配额（ApiKeyFilter + 协议族策略 + best-effort 配额）

数据面 `/v1/*` 目前完全无鉴权（ADR-0009 规划的「identity 鉴权/配额」未落地），而 `api_keys` 表（有效性/模型白名单/渠道白名单/配额/过期）与管理面 `ApiKeyCase` 均已就绪。经 grill 收敛（Q1–Q7）拍板：认证分两层落地——trigger 层 `ApiKeyFilter` 做路径白名单与 key 存在性快速失败，case 层注入 identity 认证接口做权威校验与配额；配额本轮写入，流式用量 tee 旁路解析、best-effort。

**Status**: accepted

## Decision

### 1. 两层认证

- **trigger 层 `ApiKeyFilter`**（`OncePerRequestFilter`，只扫数据面路径）：
  - **免鉴权白名单直接放行**：`/health`、`/api/v1/` 前缀、`/mcp`、`/mcp/sse` 前缀、`/v1/models` 及子路径（models 公开免鉴权是用户拍板，与 OpenAI「models 需鉴权」语义不同，属刻意偏差）；
  - 其余数据面路径按**协议族**提取调用方 key：`/v1/messages` 取 `x-api-key` 头，其余 `/v1/*` 取 `Authorization: Bearer` 头（剥离前缀）；取不到 → 立即 401（错误体按族，本轮仅有 OpenAI 族）；
  - 取到 key → 放入 request attribute（如 `INBOUND_API_KEY`）放行，**filter 不查库、不做白名单**——保持薄。
- **case 层权威认证**（`ProxyCase` 注入 `IApiKeyCase`，接口落 api 根包，符合 AGENTS.md case 接口约定）：从 request attribute 取 key + 按协议族从 body 提取 model → 认证「key 有效（精确匹配 + status=1 + 未过期）+ 模型白名单 + 配额预检」→ 通过后才 `forward`。

### 2. 协议族策略（path 为键，model 是输入）

策略按 **path（协议族）** 注册三项能力：`{key 提取, model 提取, 错误体 codec}`。header 方案只由 path 决定（messages→x-api-key，其余→Bearer），与 model 无关；model 从各族 body 提取（chat/responses/completions/embeddings/speech→`body.model`；transcriptions→multipart 表单；models→无），提取后用于白名单校验与后续路由。**model 不是注册表的键**。本轮注册表只含 OpenAI 兼容族（chat/completions/embeddings/responses）；messages/audio 下轮按各自 codec 扩展。

### 3. 转换分层（重申并落定）

- **出站**（网关 → 上游厂商）协议转换：内聚于**渠道适配器**（ADR-0006，现状如此）——一渠道一上游协议，一对一；
- **入站**（下游客户端 → 网关）协议形状转换：留 **case 层 codec**（ADR-0009 预留口子），**不放适配器**——入站协议与厂商无关，同一渠道可被 OpenAI SDK 与 Anthropic SDK 同时调用，入站转换放适配器会 N×M 爆炸（入站协议 × 厂商）。本轮不实现 messages/audio 入站 codec，结构按此预留：`MessagesCase` = Claude 入站 → OpenAI 格式 → `IDispatchInvoker` → 响应转回 Claude 形状。

### 4. 认证深度（Q4=b）

本轮生效：**key 有效性 + 模型白名单**。`IApiKeyCase.authenticate(key, model, channelId)` 在 `channelId == null` 时跳过渠道白名单检查（本轮调用传 null）。
渠道白名单本轮**不强制**（字段可配但不生效，管理面已知），下轮以「`ProxyRequest` 携带 `allowedChannels` 过滤集合、`ChannelDispatchService` 路由时只考虑命中集合的渠道，无命中 → 403」落地——身份知识不泄漏进 channel 域，只传集合。

### 5. 配额（Q7=A）

- **触发点**：请求前不预扣，响应完成后按 usage 记；只对 2xx 成功响应记，4xx/5xx 不记。
- **非流式**：从 `ForwardResult` 解析 `usage`（`TokenUsage`），累加 `totalTokens`；
- **流式**：`ProxyCase.write` 字节透传时 **tee 旁路解析**——边把 SSE 字节发给客户端，边按行扫描 `data:` 帧，命中 `usage` 即解析记账。解析容错链：`total_tokens` → `prompt_tokens + completion_tokens` 求和 → 均无则跳过。
- **适配器注入 stream_options（spec: stream-usage-quota 修订）**：OpenAI 兼容适配器在流式转发前，若请求体未带 `stream_options` 则注入 `{"include_usage":true}`，客户端已带则保留不覆盖；注入由渠道 `config` 开关控制（默认开启，不支持该参数的上游按渠道关闭）；Claude/Gemini 等非兼容适配器不注入。此为对「本轮不注入」的修订。
- **best-effort 红线**：解析失败静默跳过、不估算、不伪造、**绝不阻断透传**；记不到不是 bug，是上游行为（见 Consequences 已知缺口）。
- **超限拒单**：认证时预检 `quota_limit > 0 && quota_used >= quota_limit` → 数据面 403 `insufficient_quota`（管理面复用 ResponseCode.FORBIDDEN，不新增码）。

### 6. 错误口径（Q6 采纳）

- 缺/无效/停用/过期 key → **401** `invalid_api_key`（OpenAI 族错误体）；
- 模型不在白名单 → **404** `model_not_found`（与现有 routed=false 同文案，不透漏模型存在性）；
- 渠道白名单（下轮）/配额超限 → **403**；
- `/v1/messages` 族一律 Anthropic 错误体 `{"type":"error","error":{"type":"authentication_error",…}}`（本轮未实现端点，codec 预留）。

### 7. 本轮端点范围

认证基础设施 + `chat/completions`、`completions`、`embeddings`、`responses`（OpenAI 兼容透传）+ `/v1/models`（新控制器，公开）与 `/health`、`/api/v1/health`（新控制器，免认证）。`/v1/messages` 与 `/v1/audio/*`（multipart 字节透传）下轮各自立项。

## Considered Options

- **认证只放 Filter 或只放 case**：否决。filter 快速失败（401 挡住未认证流量、不占业务线程）与 case 权威认证（白名单/配额决策在 identity 域）各司其职，两层都留。
- **渠道白名单分发后校验 / 不做**：分发后校验则上游已发出（成本已产生）；不做则管理面配置误导。选「过滤集合传入 dispatch」（Q4 方案 b），下轮落地。
- **流式配额方案 B（下轮做）/C（字节估算）**：B 使流式配额本轮缺失；C 不准只够熔断。选 A（tee 旁路解析）。
- **注入 stream_options 的位置**：全局强制注入否决（非全部上游支持 400 风险、覆盖客户端配置）；选**适配器层注入**（spec: stream-usage-quota）——按渠道类型隔离，仅 OpenAI 兼容族注入，默认开启 + 渠道 config 开关可关，客户端已带则保留。
- **/v1/models 需鉴权（OpenAI 语义）**：用户拍板公开免鉴权，采纳并记录偏差。

## Consequences

- **已知缺口**：默认注入 `stream_options` 后 OpenAI 兼容流式请求可记账；仍记不到的情形——客户端显式 `include_usage:false`、渠道配置关闭注入、上游不支持该参数、断流、SSE 格式漂移（均为 best-effort，不兜底、不估算）。
- `IApiKeyCase` 接口新增（api 根包）；`ApiKeyCase` 支持 `channelId==null` 跳过渠道白名单；认证编排新增配额预检。
- `ProxyCase` 扩展：入站解析后增加认证步骤（接收 filter 放入的 key），`prepare` 签名相应扩展；流式 write 挂 tee 解析。
- `ApiKeyFilter` 注册于数据面路径；管理面 `/admin/api/**` 不受影响（`GlobalExceptionHandler` 仍只扫 admin 包）。
- GLOSSARY 新增「ApiKeyFilter」「协议族」术语，修订「quota」「Proxy 域」条目（已落）。
- 后续扩展轴：加入站协议 = 加 case codec + 注册表条目；加厂商 = 加适配器；渠道白名单 = dispatch 过滤集合。
