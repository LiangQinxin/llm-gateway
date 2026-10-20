package cn.qx.infrastructure.adapter.port.llm;

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
}
