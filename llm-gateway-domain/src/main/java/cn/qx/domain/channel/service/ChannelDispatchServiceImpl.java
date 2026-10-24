package cn.qx.domain.channel.service;

import cn.qx.domain.channel.adapter.port.IDispatchInvoker;
import cn.qx.domain.channel.adapter.port.IForwardingAdaptor;
import cn.qx.domain.channel.adapter.port.ILlmAdaptor;
import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.ChannelConfig;
import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ForwardResult;
import cn.qx.domain.channel.model.valobj.ModelMapping;
import cn.qx.domain.channel.model.valobj.ProxyRequest;
import cn.qx.domain.channel.model.valobj.StreamResult;
import cn.qx.domain.channel.service.selector.IChannelSelector;
import cn.qx.types.exception.UnsupportedProtocolFieldException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 渠道域统一路由器：构建 failover 队列、逐渠道尝试并在可转移失败时切换。
 */
@Slf4j
@Service
public class ChannelDispatchServiceImpl implements IChannelDispatchService, IDispatchInvoker {

    /** 渠道仓储（查询启用渠道，按优先级/权重排序返回） */
    private final IChannelRepository channelRepository;
    /** 请求级 failover 队列选择器 */
    private final IChannelSelector channelSelector;
    /** 协议适配器注册表：key=渠道类型，value=对应 LLM 适配器 */
    private final Map<String, ILlmAdaptor> adaptors = new ConcurrentHashMap<>();
    /** 可选总尝试预算（null=不设上限，最大尝试次数=retryCount+1） */
    private final Integer retryCount;

    public ChannelDispatchServiceImpl(IChannelRepository channelRepository,
                                      IChannelSelector channelSelector,
                                      List<ILlmAdaptor> adaptors,
                                      @Value("${dispatch.retry-count:#{null}}") Integer retryCount) {
        this.channelRepository = channelRepository;
        this.channelSelector = channelSelector;
        this.retryCount = retryCount;
        for (ILlmAdaptor adaptor : adaptors) {
            this.adaptors.put(adaptor.channelType(), adaptor);
        }
    }

    @Override
    public DispatchOutcome invoke(ProxyRequest request) {
        if (request == null || request.getModel() == null || request.getModel().isBlank()) {
            return DispatchOutcome.notFound(request == null ? "" : request.getModel());
        }
        List<Channel> channels = channelRepository.listEnabledOrdered();
        if (channels.isEmpty()) {
            return DispatchOutcome.noChannel(request.getModel());
        }
        Random random = ThreadLocalRandom.current();
        List<Channel> queue = channelSelector.buildFailoverQueue(channels, request.getModel(), random);
        if (queue.isEmpty()) {
            return DispatchOutcome.notFound(request.getModel());
        }

        JSONObject body = request.getBody() == null ? new JSONObject() : request.getBody();
        request.setBody(body);
        List<String> attempts = new ArrayList<>();
        Set<String> attemptedChannelIds = new HashSet<>();
        ForwardResult lastUnaryFailure = null;
        StreamResult lastStreamFailure = null;
        boolean lastFailureNetwork = false;
        Channel lastAttemptedChannel = null;
        String lastAttemptedModel = null;
        int maxAttempts = retryCount == null
                ? queue.size()
                : Math.min(Math.max(1, retryCount + 1), queue.size());

        for (int i = 0; i < queue.size(); i++) {
            if (attempts.size() >= maxAttempts) {
                break;
            }
            Channel channel = queue.get(i);
            if (!attemptedChannelIds.add(channel.getId())) {
                continue;
            }
            ILlmAdaptor adaptor = adaptors.get(channel.getType());
            if (adaptor == null) {
                log.error("渠道类型无适配器，跳过: channel={}, type={}",
                        channel.getId(), channel.getType());
                continue;
            }
            if (!(adaptor instanceof IForwardingAdaptor)) {
                log.error("渠道适配器不具备转发能力，跳过: channel={}, type={}",
                        channel.getId(), channel.getType());
                continue;
            }

            String upstreamModel = new ModelMapping(channel.activeModelMapping())
                    .resolve(request.getModel(), random);
            body.put("model", upstreamModel);
            lastAttemptedChannel = channel;
            lastAttemptedModel = upstreamModel;
            ChannelConfig config = channel.toChannelConfig();
            // 路由已完成一次性映射，适配器只透传最终模型，避免链式映射二次改写。
            config.setModelMapping(new JSONObject());

            long start = System.currentTimeMillis();
            try {
                if (request.isStream()) {
                    StreamResult result = adaptor.forwardStream(request, config);
                    if (!shouldFailover(result.getStatusCode())) {
                        log.info("路由命中: requested={}, upstream={}, channel={}, stream=true, elapsed={}ms",
                                request.getModel(), upstreamModel, channel.getId(),
                                System.currentTimeMillis() - start);
                        return DispatchOutcome.builder()
                                .routed(true)
                                .streaming(result)
                                .channelId(channel.getId())
                                .channelName(channel.getName())
                                .upstreamModel(upstreamModel)
                                .attemptCount(attempts.size() + 1)
                                .build();
                    }
                    lastUnaryFailure = null;
                    lastStreamFailure = result;
                    lastFailureNetwork = false;
                    recordFailure(channel, upstreamModel, "HTTP " + result.getStatusCode(),
                            start, attempts);
                    if (attempts.size() >= maxAttempts || !hasRemainingCandidate(queue, i)) {
                        return unaryStreamingExhausted(
                                result, attempts, lastAttemptedChannel, lastAttemptedModel);
                    }
                    closeQuietly(result);
                } else {
                    ForwardResult result = adaptor.forward(request, config);
                    if (!shouldFailover(result.getStatusCode())) {
                        log.info("路由命中: requested={}, upstream={}, channel={}, stream=false, status={}, elapsed={}ms",
                                request.getModel(), upstreamModel, channel.getId(),
                                result.getStatusCode(), System.currentTimeMillis() - start);
                        return DispatchOutcome.builder()
                                .routed(true)
                                .unary(result)
                                .channelId(channel.getId())
                                .channelName(channel.getName())
                                .upstreamModel(upstreamModel)
                                .attemptCount(attempts.size() + 1)
                                .build();
                    }
                    lastUnaryFailure = result;
                    lastStreamFailure = null;
                    lastFailureNetwork = false;
                    recordFailure(channel, upstreamModel, "HTTP " + result.getStatusCode(),
                            start, attempts);
                    if (attempts.size() >= maxAttempts || !hasRemainingCandidate(queue, i)) {
                        return unaryExhausted(
                                result, attempts, lastAttemptedChannel, lastAttemptedModel);
                    }
                }
            } catch (UnsupportedProtocolFieldException e) {
                throw e;
            } catch (RuntimeException e) {
                if (!isNetworkFailure(e)) {
                    throw e;
                }
                lastUnaryFailure = null;
                lastStreamFailure = null;
                lastFailureNetwork = true;
                recordFailure(channel, upstreamModel, e.getMessage(), start, attempts);
                if (attempts.size() >= maxAttempts || !hasRemainingCandidate(queue, i)) {
                    return upstreamUnavailable(
                            attempts, lastAttemptedChannel, lastAttemptedModel);
                }
            }
        }
        if (lastFailureNetwork) {
            return upstreamUnavailable(attempts, lastAttemptedChannel, lastAttemptedModel);
        }
        if (lastStreamFailure != null) {
            return unaryStreamingExhausted(
                    lastStreamFailure, attempts, lastAttemptedChannel, lastAttemptedModel);
        }
        if (lastUnaryFailure != null) {
            return unaryExhausted(
                    lastUnaryFailure, attempts, lastAttemptedChannel, lastAttemptedModel);
        }
        throw new IllegalStateException("failover 队列无可用转发适配器: model=" + request.getModel());
    }

    /** 启动期校验所有启用渠道都有可用适配器。 */
    @PostConstruct
    public void validateEnabledChannelTypes() {
        for (Channel channel : channelRepository.listEnabledOrdered()) {
            ILlmAdaptor adaptor = adaptors.get(channel.getType());
            if (adaptor == null || !(adaptor instanceof IForwardingAdaptor)) {
                throw new IllegalStateException("启用渠道缺少适配器: type=" + channel.getType()
                        + ", channel=" + channel.getId());
            }
        }
    }

    private boolean hasRemainingCandidate(List<Channel> queue, int index) {
        for (int i = index + 1; i < queue.size(); i++) {
            ILlmAdaptor adaptor = adaptors.get(queue.get(i).getType());
            if (adaptor instanceof IForwardingAdaptor) {
                return true;
            }
        }
        return false;
    }

    private static void recordFailure(Channel channel,
                                      String upstreamModel,
                                      String reason,
                                      long start,
                                      List<String> attempts) {
        String detail = channel.getId() + ": " + reason;
        attempts.add(detail);
        log.warn("渠道尝试失败: channel={}, type={}, requestedUpstream={}, reason={}, elapsed={}ms",
                channel.getId(), channel.getType(), upstreamModel, reason,
                System.currentTimeMillis() - start);
    }

    private static boolean shouldFailover(int statusCode) {
        return statusCode >= 500 || statusCode == 429
                || statusCode == 401 || statusCode == 403;
    }

    private static DispatchOutcome unaryExhausted(ForwardResult result,
                                                  List<String> attempts,
                                                  Channel channel,
                                                  String upstreamModel) {
        return DispatchOutcome.builder()
                .routed(true)
                .unary(result)
                .errorMessage(summary(attempts))
                .channelId(channel == null ? null : channel.getId())
                .channelName(channel == null ? null : channel.getName())
                .upstreamModel(upstreamModel)
                .attemptCount(attempts.size())
                .build();
    }

    private static DispatchOutcome unaryStreamingExhausted(StreamResult result,
                                                           List<String> attempts,
                                                           Channel channel,
                                                           String upstreamModel) {
        String raw = "";
        if (result.getBody() != null) {
            try {
                raw = new String(result.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("读取最终流式错误体失败: {}", e.getMessage());
            }
        }
        JSONObject body;
        try {
            body = JSON.parseObject(raw);
            if (body == null) {
                body = new JSONObject();
                body.put("raw", raw);
            }
        } catch (Exception e) {
            body = new JSONObject();
            body.put("raw", raw);
        }
        closeQuietly(result);
        return unaryExhausted(ForwardResult.builder()
                .statusCode(result.getStatusCode())
                .raw(raw)
                .body(body)
                .build(), attempts, channel, upstreamModel);
    }

    private static DispatchOutcome upstreamUnavailable(List<String> attempts,
                                                       Channel channel,
                                                       String upstreamModel) {
        JSONObject detail = new JSONObject();
        detail.put("message", "所有上游渠道均不可用");
        detail.put("type", "upstream_error");
        detail.put("code", "upstream_unavailable");
        JSONObject body = new JSONObject();
        body.put("error", detail);
        ForwardResult result = ForwardResult.builder()
                .statusCode(502)
                .body(body)
                .raw(body.toJSONString())
                .build();
        return DispatchOutcome.builder()
                .routed(true)
                .unary(result)
                .errorMessage(summary(attempts))
                .channelId(channel == null ? null : channel.getId())
                .channelName(channel == null ? null : channel.getName())
                .upstreamModel(upstreamModel)
                .attemptCount(attempts.size())
                .build();
    }

    private static String summary(List<String> attempts) {
        return "all channels failed: [" + String.join(", ", attempts) + "]";
    }

    private static void closeQuietly(StreamResult result) {
        if (result.getBody() != null) {
            try {
                result.getBody().close();
            } catch (IOException e) {
                log.warn("关闭失败渠道流时忽略异常: {}", e.getMessage());
            }
        }
        if (result.getCancel() != null) {
            result.getCancel().cancel();
        }
    }

    private static boolean isNetworkFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
