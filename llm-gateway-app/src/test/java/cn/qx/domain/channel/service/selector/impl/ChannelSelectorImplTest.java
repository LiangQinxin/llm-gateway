package cn.qx.domain.channel.service.selector.impl;

import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.service.selector.IChannelSelector;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ChannelSelector 容器装配测试：经接口验证队列内容与统计性质。
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ChannelSelectorImplTest {

    /** 被测选择器 */
    @Resource
    private IChannelSelector selector;

    /**
     * 模型过滤：精确模型、空模型通配、mapping key 命中，其余排除。
     */
    @Test
    public void filters_by_exact_model_wildcard_or_mapping_key() {
        Channel exact = channel("exact", 100, 1, "[\"gpt-4o\"]", "{}");
        Channel wildcard = channel("wildcard", 90, 1, "[]", "{}");
        Channel mapped = channel("mapped", 80, 1, "[]", "{\"gpt-4o\":\"deepseek-chat\"}");
        Channel unmatched = channel("unmatched", 70, 1, "[\"claude\"]", "{}");

        List<Channel> queue = selector.buildFailoverQueue(
                List.of(exact, wildcard, mapped, unmatched), "gpt-4o", new Random(1));
        log.info("模型过滤队列: {}", JSON.toJSONString(queue));

        Set<String> ids = ids(queue);
        assertEquals(3, queue.size());
        assertTrue(ids.contains(exact.getId()));
        assertTrue(ids.contains(wildcard.getId()));
        assertTrue(ids.contains(mapped.getId()));
    }

    /**
     * 高优先级组的所有非零权重渠道先于低优先级组；零权重渠道统一兜底。
     */
    @Test
    public void higher_priority_weighted_group_precedes_lower_priority_and_zero_weight() {
        Channel high = channel("high", 100, 5, "[\"m\"]", "{}");
        Channel low = channel("low", 10, 99, "[\"m\"]", "{}");
        Channel zero = channel("zero", 200, 0, "[\"m\"]", "{}");

        List<Channel> queue = selector.buildFailoverQueue(
                List.of(zero, high, low), "m", new Random(2));
        log.info("优先级队列: {}", orderedIds(queue));

        assertEquals(List.of("high", "low", "zero"), orderedIds(queue));
    }

    /**
     * 整组零权重时退化为 priority DESC、weight DESC 的稳定原序。
     */
    @Test
    public void all_zero_weight_group_falls_back_to_priority_then_weight_order() {
        Channel highPriority = channel("p100", 100, 0, "[\"m\"]", "{}");
        Channel lowPriorityHeavy = channel("p10-w-1", 10, -1, "[\"m\"]", "{}");
        Channel lowPriorityLight = channel("p10-w-2", 10, -2, "[\"m\"]", "{}");

        List<Channel> queue = selector.buildFailoverQueue(
                List.of(lowPriorityLight, lowPriorityHeavy, highPriority), "m", new Random(3));
        log.info("零权重兜底队列: {}", orderedIds(queue));

        assertEquals(List.of("p100", "p10-w-1", "p10-w-2"), orderedIds(queue));
    }

    /**
     * 组内不放回轮盘赌：每个渠道恰好出现一次，权重 5:3:2 的长期占比近似。
     */
    @Test
    public void weighted_roulette_keeps_each_channel_once_and_follows_weight_distribution() {
        Channel heavy = channel("heavy", 100, 5, "[\"m\"]", "{}");
        Channel medium = channel("medium", 100, 3, "[\"m\"]", "{}");
        Channel light = channel("light", 100, 2, "[\"m\"]", "{}");
        List<Channel> channels = new ArrayList<>(List.of(heavy, medium, light));
        Random random = new Random(42);
        Map<String, Integer> firstCounts = new HashMap<>();
        int rounds = 6000;

        for (int i = 0; i < rounds; i++) {
            List<Channel> queue = selector.buildFailoverQueue(channels, "m", random);
            assertEquals("每个渠道必须恰好出现一次", 3, queue.size());
            assertEquals("队列不得重复渠道", 3, ids(queue).size());
            firstCounts.merge(queue.get(0).getId(), 1, Integer::sum);
        }
        log.info("5:3:2 首轮分布: {}", firstCounts);

        assertApprox(firstCounts.get("heavy"), rounds, 0.50);
        assertApprox(firstCounts.get("medium"), rounds, 0.30);
        assertApprox(firstCounts.get("light"), rounds, 0.20);
    }

    /** 断言占比在期望值正负 5 个百分点内。 */
    private static void assertApprox(int actual, int rounds, double expected) {
        double ratio = actual / (double) rounds;
        assertEquals("权重分布偏离过大: actual=" + ratio, expected, ratio, 0.05);
    }

    private static Set<String> ids(List<Channel> channels) {
        Set<String> ids = new HashSet<>();
        for (Channel channel : channels) {
            ids.add(channel.getId());
        }
        return ids;
    }

    private static List<String> orderedIds(List<Channel> channels) {
        return channels.stream().map(Channel::getId).toList();
    }

    private static Channel channel(String id, int priority, int weight, String models, String mapping) {
        return Channel.builder()
                .id(id)
                .name(id)
                .type("openai")
                .priority(priority)
                .weight(weight)
                .models(models)
                .modelMapping(mapping)
                .build();
    }
}
