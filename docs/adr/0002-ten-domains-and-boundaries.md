# ADR-0002: 十域划分与边界（proxy 域范围、quota 归属）

Status: accepted；其中 proxy 域边界「协议形状转换在 codec（protocol 模块）」条目被 [ADR-0006](./0006-adaptor-owns-protocol-conversion.md) 取代。

领域层固定为十域：proxy/channel/identity/security/audit/account/settings/knowledge/wiki/mcp；quota 不独立成域；identity 与 account 的边界按调用方/上游侧区分。

## Decision

- **十域**：每域同构 `model/（aggregate·entity·valobj）` + `service/` + `adapter/（port·repository）`；不膨胀为十一域；
- **identity 域 = 调用方 API Key 身份/鉴权/配额/白名单**；**account 域 = 上游第三方账号**（auth_provider：codex/gemini/grok/kimi 登录、刷新、配额）。quota 不独立成域：API Key 配额归 identity，第三方账号配额归 account；
- **proxy 域 = 转发主链路决策**：路由计划、渠道分发、加权选 key、单次尝试判定、流监督、执行编排。边界：HTTP 发送在 infrastructure（ILlmGateway 实现），~~协议形状转换在 codec（protocol 模块）~~（被 ADR-0006 取代：上游协议形状转换在渠道适配器——channel 域 `ILlmAdaptor` 端口 + infrastructure 实现；入站侧统一 OpenAI 协议，本期不做入站多协议检测），渠道身份（channel_identity）在 channel 域，特性开关（feature_flags）在 settings 域；
- **管理面命令分组（约 110+ 个管理命令）暂缓**：先做三表基础实现（见 ADR-0005），分组方案（十域 Controller + SystemController）留待管理面阶段收敛。

## Consequences

术语以 `GLOSSARY.md` 为准（identity/account/proxy/codec 等已钉死）；proxy 主链路阶段直接复用三表阶段落地的 `findEnabledForRoute`、`authenticate`、`recordUsage`。
