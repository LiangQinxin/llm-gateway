package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Anthropic Messages 适配器。
 */
@Component("anthropic")
public class AnthropicAdaptor extends AbstractLlmAdaptor {

    @Override
    public String channelType() {
        return "anthropic";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.anthropic.com";
    }

    @Override
    public List<NativeEndpoint> nativeEndpoints() {
        return List.of(NativeEndpoint.builder()
                .protocol(ProtocolFamily.MS)
                .path("/v1/messages")
                .authStyle(AuthStyle.X_API_KEY)
                .build());
    }
}
