package cn.qx.domain.channel.service;

import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ModelMapping;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 渠道域统一路由器：匹配、模型映射预烘焙、适配器分发。
 */
@Slf4j
@Service
public class ChannelDispatchService implements IDispatchInvoker {

    private final IChannelRepository channelRepository;
    private final Map<String, ILlmAdaptor> adaptors;

    public ChannelDispatchService(IChannelRepository channelRepository,
                                  Map<String, ILlmAdaptor> adaptors) {
        this.channelRepository = channelRepository;
        this.adaptors = adaptors;
    }

    @Override
    public DispatchOutcome invoke(ProxyRequest request) {
        if (request == null || request.getModel() == null || request.getModel().isBlank()) {
            return DispatchOutcome.notFound(request == null ? "" : request.getModel());
        }
        List<Channel> channels = channelRepository.listEnabledOrdered();
        for (Channel channel : channels) {
            if (!supports(channel, request.getModel())) {
                continue;
            }
            JSONObject body = request.getBody() == null ? new JSONObject() : request.getBody();
            request.setBody(body);
            String upstreamModel = new ModelMapping(channel.activeModelMapping())
                    .resolve(request.getModel(), ThreadLocalRandom.current());
            body.put("model", upstreamModel);
            ChannelConfig config = channel.toChannelConfig();
            // 路由已完成一次性映射，适配器只透传最终模型，避免链式映射二次改写。
            config.setModelMapping(new JSONObject());
            ILlmAdaptor adaptor = adaptors.get(channel.getType());
            if (adaptor == null) {
                throw new IllegalStateException("渠道类型无适配器: " + channel.getType());
            }
            log.info("路由命中: requested={}, upstream={}, channel={}, stream={}",
                    request.getModel(), upstreamModel, channel.getId(), request.isStream());
            return request.isStream()
                    ? DispatchOutcome.streaming(adaptor.forwardStream(request, config))
                    : DispatchOutcome.unary(adaptor.forward(request, config));
        }
        return DispatchOutcome.notFound(request.getModel());
    }

    /** 启动期校验所有启用渠道都有可用适配器。 */
    @PostConstruct
    public void validateEnabledChannelTypes() {
        for (Channel channel : channelRepository.listEnabledOrdered()) {
            if (!adaptors.containsKey(channel.getType())) {
                throw new IllegalStateException("启用渠道缺少适配器: type=" + channel.getType()
                        + ", channel=" + channel.getId());
            }
        }
    }

    private static boolean supports(Channel channel, String model) {
        return channel.activeModels().contains(model)
                || channel.activeModelMapping().containsKey(model);
    }
}
