package cn.qx.domain.channel.service.selector.impl;

import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.service.selector.IChannelSelector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * 渠道选择器实现：模型过滤 → priority 分组 → 组内不放回轮盘赌 → 零权重兜底。
 */
@Component
public class ChannelSelectorImpl implements IChannelSelector {

    @Override
    public List<Channel> buildFailoverQueue(List<Channel> enabledOrdered, String model, Random random) {
        Objects.requireNonNull(random, "random");
        if (enabledOrdered == null || enabledOrdered.isEmpty() || model == null || model.isBlank()) {
            return List.of();
        }

        List<Channel> matched = enabledOrdered.stream()
                .filter(Objects::nonNull)
                .filter(channel -> supports(channel, model))
                .sorted(ChannelSelectorImpl::comparePriorityWeight)
                .toList();
        if (matched.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<Channel>> groups = new LinkedHashMap<>();
        for (Channel channel : matched) {
            groups.computeIfAbsent(priority(channel), ignored -> new ArrayList<>()).add(channel);
        }

        List<Channel> queue = new ArrayList<>(matched.size());
        List<Channel> zeroWeightFallback = new ArrayList<>();
        for (List<Channel> group : groups.values()) {
            List<Channel> candidates = new ArrayList<>();
            for (Channel channel : group) {
                if (weight(channel) > 0) {
                    candidates.add(channel);
                } else {
                    zeroWeightFallback.add(channel);
                }
            }
            while (!candidates.isEmpty()) {
                queue.add(drawWithoutReplacement(candidates, random));
            }
        }
        queue.addAll(zeroWeightFallback);
        return List.copyOf(queue);
    }

    private static Channel drawWithoutReplacement(List<Channel> candidates, Random random) {
        long totalWeight = 0;
        for (Channel candidate : candidates) {
            totalWeight += weight(candidate);
        }
        double point = random.nextDouble() * totalWeight;
        long cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weight(candidates.get(i));
            if (cumulative > point) {
                return candidates.remove(i);
            }
        }
        return candidates.remove(candidates.size() - 1);
    }

    private static boolean supports(Channel channel, String model) {
        List<String> models = channel.activeModels();
        return models.contains(model)
                || models.isEmpty()
                || channel.activeModelMapping().containsKey(model);
    }

    private static int priority(Channel channel) {
        return channel.getPriority() == null ? 0 : channel.getPriority();
    }

    private static int weight(Channel channel) {
        return channel.getWeight() == null || channel.getWeight() <= 0 ? 0 : channel.getWeight();
    }

    private static int rawWeight(Channel channel) {
        return channel.getWeight() == null ? 0 : channel.getWeight();
    }

    private static int comparePriorityWeight(Channel left, Channel right) {
        int priorityOrder = Integer.compare(priority(right), priority(left));
        return priorityOrder != 0 ? priorityOrder : Integer.compare(rawWeight(right), rawWeight(left));
    }
}
