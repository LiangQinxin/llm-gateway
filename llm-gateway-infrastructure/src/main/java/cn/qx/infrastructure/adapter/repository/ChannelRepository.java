package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.channel.adapter.repository.IChannelRepository;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.infrastructure.dao.ChannelMapper;
import cn.qx.infrastructure.dao.po.ChannelPO;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * 渠道仓储实现——MyBatis 读写 channels 表。
 * <p>PO（ChannelPO）与领域实体（Channel）字段同构，出入参在此转换。
 */
@Repository
public class ChannelRepository implements IChannelRepository {

    /** 渠道表 Mapper */
    @Resource
    private ChannelMapper channelMapper;

    @Override
    public Channel getById(String id) {
        return toEntity(channelMapper.queryById(id));
    }

    @Override
    public Channel getByName(String name) {
        return toEntity(channelMapper.queryByName(name));
    }

    @Override
    public List<Channel> listAll() {
        return channelMapper.queryAll().stream().map(ChannelRepository::toEntity).toList();
    }

    @Override
    public List<Channel> listEnabledOrdered() {
        return channelMapper.queryEnabledOrdered().stream().map(ChannelRepository::toEntity).toList();
    }

    @Override
    public void updateLastTest(String id, boolean ok, String lastTestAt) {
        channelMapper.updateLastTest(id, ok ? 1 : 0, lastTestAt);
    }

    @Override
    public void insert(Channel channel) {
        channelMapper.insert(toPO(channel));
    }

    @Override
    public int update(Channel channel) {
        return channelMapper.update(toPO(channel));
    }

    @Override
    public int updateStatus(String id, Integer status, String updatedAt) {
        return channelMapper.updateStatus(id, status, updatedAt);
    }

    @Override
    public int deleteById(String id) {
        return channelMapper.deleteById(id);
    }

    private static ChannelPO toPO(Channel entity) {
        ChannelPO po = new ChannelPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    private static Channel toEntity(ChannelPO po) {
        if (po == null) {
            return null;
        }
        Channel entity = new Channel();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }
}
