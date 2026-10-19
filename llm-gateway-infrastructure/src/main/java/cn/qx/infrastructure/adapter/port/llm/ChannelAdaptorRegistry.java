package cn.qx.infrastructure.adapter.port.llm;

import cn.qx.domain.channel.adapter.port.IChannelAdaptorRegistry;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道适配器注册表——按渠道类型返回对应适配器。
 * <p>Spring 注入全部 ILlmAdaptor 实现，按 channelType() 匹配；
 * Custom 适配器加入后可作为未知类型的兜底。
 */
@Component
public class ChannelAdaptorRegistry implements IChannelAdaptorRegistry {

    /** 按渠道类型索引的适配器表 */
    private final Map<String, ILlmAdaptor> adaptors = new HashMap<>();

    /**
     * 构造注册表，收集全部适配器实现。
     *
     * @param adaptorList Spring 注入的适配器实现列表
     */
    public ChannelAdaptorRegistry(List<ILlmAdaptor> adaptorList) {
        for (ILlmAdaptor adaptor : adaptorList) {
            adaptors.put(adaptor.channelType(), adaptor);
        }
    }

    /**
     * 按渠道类型获取适配器；未知类型抛 IllegalArgumentException。
     *
     * @param channelType 渠道类型标识
     * @return 对应适配器
     */
    @Override
    public ILlmAdaptor getAdaptor(String channelType) {
        ILlmAdaptor adaptor = adaptors.get(channelType);
        if (adaptor == null) {
            throw new IllegalArgumentException("不支持的渠道类型: " + channelType);
        }
        return adaptor;
    }
}
