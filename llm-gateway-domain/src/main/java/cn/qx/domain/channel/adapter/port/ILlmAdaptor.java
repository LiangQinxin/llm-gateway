package cn.qx.domain.channel.adapter.port;

import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.model.valobj.TestResult;

import java.util.List;

/**
 * 渠道适配器端口——渠道对外部 LLM 供应商的协议能力（ADR-0006）。
 * <ul>
 *   <li>OpenAI 兼容渠道：透传 + 模型映射；</li>
 *   <li>非兼容渠道（Claude Messages、Gemini）：适配器内做协议转换。</li>
 * </ul>
 * 由 infrastructure 提供各厂商实现；转发链路与管理面测试只依赖本端口。
 */
public interface ILlmAdaptor {

    /** 渠道类型标识：deepseek / openai / claude / gemini / custom */
    String channelType();

    /** 默认支持的模型列表 */
    List<String> defaultModels();

    /** 默认 API 地址 */
    String defaultBaseUrl();

    /** 有序原生端点表；第一个为 primary。 */
    List<NativeEndpoint> nativeEndpoints();

    /** 渠道连通性测试（回写 last_test_* 由调用方负责） */
    TestResult test(ChannelConfig config);

    /** 非流式转发：返回状态码 + 响应体 + Token 用量 */
    ForwardResult forward(ProxyRequest request, ChannelConfig config);

    /** 流式转发：返回上游 SSE 字节流，由调用方逐段透传 */
    StreamResult forwardStream(ProxyRequest request, ChannelConfig config);
}
