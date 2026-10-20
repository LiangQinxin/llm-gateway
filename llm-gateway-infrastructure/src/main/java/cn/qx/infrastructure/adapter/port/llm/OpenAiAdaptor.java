package cn.qx.infrastructure.adapter.port.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OpenAI 官方渠道适配器。
 */
@Component("openai")
public class OpenAiAdaptor extends OpenAICompatibleAdaptor {

    @Override
    public String channelType() {
        return "openai";
    }

    @Override
    public List<String> defaultModels() {
        return List.of("gpt-4o", "gpt-4o-mini", "text-embedding-3-small");
    }

    @Override
    public String defaultBaseUrl() {
        return "https://api.openai.com/v1";
    }
}
