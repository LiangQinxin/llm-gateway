package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.infrastructure.persistent.dao.ChannelMapper;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.List;

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
    public Channel getByName(String name) {
        return channelMapper.queryByName(name);
    }

    @Override
    public List<Channel> listAll() {
        return channelMapper.queryAll();
    }

    @Override
    public List<Channel> listEnabledOrdered() {
        return channelMapper.queryEnabledOrdered();
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
    public int update(Channel channel) {
        return channelMapper.update(channel);
    }

    @Override
    public int updateStatus(String id, Integer status, String updatedAt) {
        return channelMapper.updateStatus(id, status, updatedAt);
    }

    @Override
    public int deleteById(String id) {
        return channelMapper.deleteById(id);
    }
}
