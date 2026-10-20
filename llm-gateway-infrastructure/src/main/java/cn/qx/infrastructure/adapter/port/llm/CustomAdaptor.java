package cn.qx.infrastructure.adapter.port.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自定义 OpenAI 兼容渠道适配器。
 */
@Component("custom")
public class CustomAdaptor extends OpenAICompatibleAdaptor {

    @Override
    public String channelType() {
        return "custom";
    }

    @Override
    public List<String> defaultModels() {
        return List.of();
    }

    @Override
    public String defaultBaseUrl() {
        return "";
    }
}
