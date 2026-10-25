# ADR-0006: 渠道适配器承担上游协议转换，撤销独立 protocol 模块

渠道适配器（channel 域 `ILlmAdaptor` 端口 + infrastructure 实现）在适配器内部完成 OpenAI→Claude/Gemini 等上游协议转换；撤销 ADR-0001 的 `llm-gateway-protocol` 第 8 模块与 ADR-0002 中「协议形状转换在 codec」边界。入站侧统一 OpenAI 协议，本期不做入站多协议检测与独立 SSE codec 模块。

Status: accepted

## Context

历史上曾存在双轨：legacy adaptor（openai/custom 透传 + claude/gemini 内部转换）与新建 protocol codec 模块（chat/messages/responses/gemini 双向编解码 + SSE + 入站检测）。经 grill 收敛（Q2）拍板：去掉 protocol 模块，协议转换全部内聚于适配器，避免双轨维护与二次迁移。

## Considered Options

- **保留 protocol 模块**（独立 codec 模块路线）：双向 codec 独立，trigger 入站与 infrastructure 出站共同依赖。否决：Java 版对外统一 OpenAI 协议，入站侧无多协议需求；上游差异由适配器按渠道隔离更内聚；减少一个模块的构建与维护面。
- **适配器内转换**（本 ADR）：OpenAI 兼容渠道（openai/deepseek/custom 等）透传 + 模型映射；非兼容渠道（claude/gemini）在适配器内做 OpenAI→上游格式转换。采纳。

## Consequences

- SSE 逐帧透传职责落在 proxy 域执行编排（流式响应经 OkHttp 出站、ResponseBodyEmitter 入站透传，见 ADR-0003/0008），不建独立 codec 模块；
- 渠道连通性测试、模型映射、请求清洗等协议相关逻辑统一进适配器；channel 域定义端口，infrastructure 提供实现；
- 后续若需对外暴露 Anthropic/Gemini 入站协议，再单独评估 codec 模块（届时另立 ADR）。
