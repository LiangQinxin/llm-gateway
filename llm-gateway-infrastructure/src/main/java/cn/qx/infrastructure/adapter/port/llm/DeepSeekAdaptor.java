package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DeepSeek 适配器——OpenAI 兼容协议透传。
 */
@Component("deepseek")
public class DeepSeekAdaptor extends OpenAICompatibleAdaptor {

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
}
