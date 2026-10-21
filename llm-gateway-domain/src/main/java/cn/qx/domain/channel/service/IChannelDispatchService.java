package cn.qx.domain.channel.service;

import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ProxyRequest;

/**
 * 渠道域统一路由器服务接口（领域服务必须定义接口）。
 * <p>唯一实现：{@link ChannelDispatchServiceImpl}（同时承担对 proxy 用例的
 * {@link cn.qx.domain.channel.adapter.port.IDispatchInvoker} 门面角色，ADR-0009）。
 */
public interface IChannelDispatchService {

    /** 一次渠道分发：匹配 → 模型映射预烘焙 → 适配器分发。 */
    DispatchOutcome invoke(ProxyRequest request);

    /** 启动期校验所有启用渠道都有可用适配器。 */
    void validateEnabledChannelTypes();
}
