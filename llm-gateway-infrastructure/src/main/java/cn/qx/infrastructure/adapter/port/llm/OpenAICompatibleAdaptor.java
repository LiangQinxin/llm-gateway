package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.infrastructure.adapter.port.llm.support.OpenAICompatSupport;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * OpenAI 兼容协议适配器：cc/rp 原生端点与流式 usage 注入。
 */
public abstract class OpenAICompatibleAdaptor extends AbstractLlmAdaptor {

    @Override
    public List<NativeEndpoint> nativeEndpoints() {
        return List.of(
                NativeEndpoint.builder()
                        .protocol(ProtocolFamily.CC)
                        .path("/v1/chat/completions")
                        .authStyle(AuthStyle.BEARER)
                        .build(),
                NativeEndpoint.builder()
                        .protocol(ProtocolFamily.RP)
                        .path("/v1/responses")
                        .authStyle(AuthStyle.BEARER)
                        .build());
    }

    @Override
    protected JSONObject prepareBody(JSONObject requestBody,
                                     ChannelConfig config,
                                     boolean stream,
                                     ProtocolFamily targetProtocol) {
        JSONObject body = OpenAICompatSupport.applyModelMapping(
                requestBody, config.getModelMapping());
        return stream && targetProtocol == ProtocolFamily.CC
                ? injectStreamOptions(body, config)
                : body;
    }

    private static JSONObject injectStreamOptions(JSONObject body, ChannelConfig config) {
        if (body == null || !body.getBooleanValue("stream")) {
            return body;
        }
        JSONObject extra = config.getExtra();
        if (extra != null && !extra.getBooleanValue("inject_stream_options", true)) {
            return body;
        }
        if (body.containsKey("stream_options")) {
            return body;
        }
        JSONObject copy = (JSONObject) body.clone();
        JSONObject streamOptions = new JSONObject();
        streamOptions.put("include_usage", true);
        copy.put("stream_options", streamOptions);
        return copy;
    }
}
