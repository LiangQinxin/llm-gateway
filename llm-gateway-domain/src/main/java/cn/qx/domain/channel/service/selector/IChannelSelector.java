package cn.qx.domain.channel.service.selector;

import cn.qx.domain.channel.model.entity.Channel;

import java.util.List;
import java.util.Random;

/**
 * 渠道选择器：按模型、优先级与权重构建无重复的 failover 队列。
 */
public interface IChannelSelector {

    /**
     * 构建请求级 failover 队列。
     *
     * @param enabledOrdered 已启用渠道，按 priority DESC, weight DESC 排序
     * @param model          下游请求模型
     * @param random         随机源
     * @return 每个匹配渠道恰好出现一次的有序队列
     */
    List<Channel> buildFailoverQueue(List<Channel> enabledOrdered, String model, Random random);
}
