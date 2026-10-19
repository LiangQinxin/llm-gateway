package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.infrastructure.persistent.dao.ChannelMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;

/**
 * 渠道仓储实现——MyBatis 读写 channels 表。
 */
@Repository
public class ChannelRepository implements IChannelRepository {

    /** 渠道表 Mapper */
    @Resource
    private ChannelMapper channelMapper;

    @Override
    public Channel getById(String id) {
        return channelMapper.queryById(id);
    }

    @Override
    public void updateLastTest(String id, boolean ok, String lastTestAt) {
        channelMapper.updateLastTest(id, ok ? 1 : 0, lastTestAt);
    }

    @Override
    public void insert(Channel channel) {
        channelMapper.insert(channel);
    }

    @Override
    public void deleteById(String id) {
        channelMapper.deleteById(id);
    }
}
