# LLMAPI

本地运行的 LLM API 网关服务，统一转换各供应商 API 兼容协议，自带可视化管理台。

## 技术栈

- **后端**：Java 17 + Spring Boot 3.4 + MyBatis + MySQL 8（HikariCP）+ OkHttp（SSE 流式）+ Google ADK + FastJSON / Guava
- **架构**：六边形分层（trigger → case → domain ← infrastructure）+ api + app 组合根，proxy / channel / identity / security / audit / account / settings / knowledge / wiki / mcp 十个领域域
- **管理台**：原生 HTML / CSS / JS，现代深色 / 浅色主题，无框架依赖

## 功能

> 各家 AI 客户端（如 Claude Code、Codex 等）往往只支持自家协议的 API Key，无法直接使用大多原厂商密钥。LLMAPI 把这些请求统一收口——对外只提供一个本地地址和一把 `sk-llmapi-*` 密钥，统一转换为兼容协议后转发给各上游厂商，客户端不用再为每家供应商单独配置。

> 直接发送的信息会原样泄露给 AI——请求里可能混着 API Key、SSH 私钥、数据库连接串，AI IDE 自动收集的上下文里可能有你的 .env 文件内容。LLMAPI 在转发前专门抓取这些风险信息，扫描、打码、阻断，确保敏感内容不出站。

- 🔍 **安全审计引擎** — 六类子扫描器（凭证 / 路径 / Unicode / 网络 / 危险工具 / 追踪）、三段制风险评分（0–100）、off / audit / warn / redact / confirm / block 六档策略、65 条内置规则 + 自定义黑白名单、请求 / 响应递归脱敏
- 🔗 **多渠道接入** — 内置 OpenAI / DeepSeek / Claude / Gemini 适配器（后续加入其他厂商），其余 OpenAI 兼容供应商（智谱、通义、Moonshot、豆包、Ollama 等）走自定义渠道；模型映射支持 1:1 或数组按权重采样，含启停与连通性测试
- 🎫 **密钥与配额** — 生成 `sk-llmapi-*` 格式访问密钥，支持模型 / 渠道白名单、过期时间；配额 0=不限、超限封顶保护，用量 best-effort 记账不阻断透传
- 📈 **仪表盘** — 聚合请求统计、Token 消耗与渠道状态
- 🧾 **全量请求日志** — 每次转发留痕、脱敏后落库只增不改，支持 detailed / brief / basic 三级保留档
- ⚖️ **优先级 + 权重调度** — 请求时做模型匹配过滤 → 优先级分组 → 组内不放回权重随机，构建每个渠道恰好出现一次的 failover 队列；失败按错误分类切换下一渠道，每渠道最多尝试一次
- 🔀 **多协议族透传** — cc（Chat Completions）/ rp（OpenAI Responses）/ ms（Claude Messages）三族入站，以 canonical 中间格式经 codec 与任意出站协议组合；cc 原生零转换透传，SSE 流式字节原样转发、错误体不包装
- 🖥️ **管理台** — 深色 / 浅色主题，Dashboard / Channels / ApiKeys / Logs / Security / Usage 六个页面

## 快速开始

### 开发环境

前置要求：JDK 17、Maven 3.6+、MySQL 8.0

```bash
# 1. 初始化数据库（库名 llm_api，含全部表结构与种子数据）
mysql -uroot -p < docs/mysql/sql/llm_api.sql

# 2. 修改本地配置（数据库账号密码，默认 root/123456；服务端口 8091）
#    llm-gateway-app/src/main/resources/application-dev.yml

# 3. 构建
mvn clean package

# 4. 启动（或直接运行 llm-gateway-app 下的 cn.qx.Application）
mvn -pl llm-gateway-app -am spring-boot:run

# 5. 打开管理台
#    http://127.0.0.1:8091
```

测试：`mvn test`（llm-gateway-app 模块使用 `@SpringBootTest` 起真实容器连本地 MySQL 跑通整条链路）。

### 使用方法

1. **配置渠道** — 在管理台「渠道」页添加上游 API 供应商（类型、Base URL、API Key、模型列表、优先级 / 权重）
2. **创建密钥** — 在管理台「密钥」页生成 `sk-llmapi-*` 格式的本地访问密钥（可设配额 / 白名单 / 过期时间）
3. **下游接入** — 在 ChatBox / NextChat / OpenAI SDK 等兼容客户端中配置：

```text
Base URL: http://127.0.0.1:8091/v1
API Key:  sk-llmapi-...
```

数据面入口（OpenAI 兼容协议）：

| 端点 | 说明 |
| --- | --- |
| `/v1/chat/completions` | Chat Completions（cc） |
| `/v1/responses` | OpenAI Responses（rp） |
| `/v1/messages` | Claude Messages（ms） |
| `/v1/completions`、`/v1/embeddings` | 兼容透传 |
| `/v1/models`、`/health` | 模型列表、健康探针 |

## 项目结构

```text
llm-gateway/
├── llm-gateway-api/             # DTO / Response / context（接口与协议无关输出对象）
├── llm-gateway-app/             # 组合根：启动入口 + 配置 + MyBatis XML + 管理台静态资源 + 集成测试
├── llm-gateway-case/            # 用例编排层（cn.qx.cases.<域>.<Xxx>Case）
├── llm-gateway-domain/          # 领域层（十域纯决策 + 端口接口）
│   └── src/main/java/cn/qx/domain/{account,audit,channel,identity,knowledge,mcp,proxy,security,settings,wiki}/
├── llm-gateway-infrastructure/  # 基础设施（MyBatis 持久化 + OkHttp 上游适配器）
│   └── .../adapter/port/llm/    # 上游适配器（openai / deepseek / anthropic / google / custom + codec）
├── llm-gateway-trigger/         # 薄 Controller + Filter（数据面 /v1/*、管理面 /admin/api/**）
├── llm-gateway-types/           # 常量 / 枚举 / 异常
├── docs/                        # 设计文档（docs/adr 决策记录、docs/mysql/sql 数据库脚本）
├── GLOSSARY.md                  # 领域术语表
├── AGENTS.md                    # Agent 协作约定
└── pom.xml                      # Maven 父工程
```

## 相关文档

- 术语表：[GLOSSARY.md](./GLOSSARY.md)
- 架构决策：[docs/adr/](./docs/adr/)
- 数据库脚本：[docs/mysql/sql/llm_api.sql](./docs/mysql/sql/llm_api.sql)
