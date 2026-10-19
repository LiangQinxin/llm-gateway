package cn.qx.domain.channel.adapter.port;

/**
 * 渠道适配器注册表端口——按渠道类型返回对应适配器。
 * <p>由 infrastructure 提供实现（Spring 注入全部适配器按类型匹配）。
 */
public interface IChannelAdaptorRegistry {

    /**
     * 按渠道类型获取适配器。
     *
     * @param channelType 渠道类型标识
     * @return 对应适配器
     * @throws IllegalArgumentException 未知渠道类型
     */
    ILlmAdaptor getAdaptor(String channelType);
}
