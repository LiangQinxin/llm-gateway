package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.model.valobj.AuthStyle;
import cn.qx.domain.channel.model.valobj.NativeEndpoint;
import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Google Gemini 的 OpenAI 兼容端点适配器。
 */
@Component("google")
public class GoogleAdaptor extends OpenAICompatibleAdaptor {

    @Override
    public String channelType() {
        return "google";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("gemini-2.0-flash", "gemini-1.5-pro");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/openai/v1";
    }

    @Override
    public List<NativeEndpoint> nativeEndpoints() {
        return List.of(NativeEndpoint.builder()
                .protocol(ProtocolFamily.CC)
                .path("/v1/chat/completions")
                .authStyle(AuthStyle.BEARER)
                .build());
    }
}
