# ADR-0001: 七模块骨架、case 编排与依赖方向

Status: accepted；其中「第 8 模块 llm-gateway-protocol」条目被 [ADR-0006](./0006-adaptor-owns-protocol-conversion.md) 取代（渠道适配器承担协议转换，撤销独立 protocol 模块）。

Java 版 llm-gateway 承接 Rust 原项目，保留 7 模块骨架（types/domain/case/app/api/trigger/infrastructure），编排在 case 层做，依赖沿用 llm-api 现状不收紧。~~并新增第 8 模块 `llm-gateway-protocol` 承载双向 codec~~（被 ADR-0006 撤销，不设第 8 模块）。

## Decision

- **case 是编排层**：`cn.qx.cases.<域>.<Xxx>Case`（@Component 注入 I*Service/I*Repository/I*Port），产出协议无关的 `<Xxx>Result`（@Data @Builder）；Controller 只做「解析 → 调 Case → 序列化」；
- **依赖沿用 llm-api 现状**：不收紧 pom 边界——trigger 可直连 domain，domain 保留 okhttp/google-adk/pdfbox 等框架依赖。六边形边界靠约定而非编译期强制，此为已接受取舍；
- ~~**新增第 8 模块 `llm-gateway-protocol`**（包 `cn.qx.protocol`，零业务依赖）：双向 codec（chat/messages/responses/gemini/legacy）+ SSE 帧切分/组装 + 入站协议检测，被 trigger 入站与 infrastructure 出站共同依赖，放任何一侧都会造成错误依赖方向~~（被 ADR-0006 取代：上游协议转换内聚于渠道适配器，SSE 逐帧透传在 proxy 执行编排，不设独立 protocol 模块）；
- **app 是组合根**（启动装配，对应 Rust lib.rs AppState）；api/types 作支撑（DTO/Response/Constants/枚举/AppException）。

## Consequences

依赖未收紧意味着领域层带框架依赖，边界靠 review 与命名约定维护；~~protocol 模块独立后 trigger/infrastructure 都只依赖它的纯转换 API~~（撤销：上游协议转换由 channel 域 `ILlmAdaptor` 端口 + infrastructure 适配器实现承担，见 ADR-0006）。

参考：`docs/三表基础实现-设计计划.md`（首批实现范围）、`GLOSSARY.md`（术语）。
