package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DeepSeek 适配器——OpenAI 兼容协议透传（cc + anthropic ms 双端点）。
 */
@Component("deepseek")
public class DeepSeekAdaptor extends AbstractLlmAdaptor {

    @Override
    public String channelType() {
        return "deepseek";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("deepseek-chat", "deepseek-coder", "deepseek-reasoner");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.deepseek.com/v1";
    }

    @Override
    public List<NativeEndpoint> nativeEndpoints() {
        return List.of(
                NativeEndpoint.builder()
                        .protocol(ProtocolFamily.CC)
                        .path("/v1/chat/completions")
                        .authStyle(AuthStyle.BEARER)
                        .build(),
                NativeEndpoint.builder()
                        .protocol(ProtocolFamily.MS)
                        .path("/anthropic/v1/messages")
                        .authStyle(AuthStyle.X_API_KEY)
                        .build());
    }

    @Override
    protected JSONObject prepareBody(JSONObject requestBody,
                                     ChannelConfig config,
                                     boolean stream,
                                     ProtocolFamily targetProtocol) {
        JSONObject body = super.prepareBody(requestBody, config, stream, targetProtocol);
        return stream && targetProtocol == ProtocolFamily.CC
                ? injectStreamOptions(body, config)
                : body;
    }

    /** OpenAI 族私有能力：cc 流式请求注入 stream_options.include_usage（渠道 extra 可关）。 */
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
