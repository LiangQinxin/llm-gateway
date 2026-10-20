package cn.qx.domain.channel.adapter.repository;

import cn.qx.domain.channel.model.entity.Channel;

import java.util.List;

/**
 * 渠道仓储端口——channels 表读写。
 * <p>由 infrastructure 提供 MyBatis 实现；用例层只依赖本端口。
 */
public interface IChannelRepository {

    /**
     * 按 ID 查询渠道。
     *
     * @param id 渠道 ID
     * @return 渠道实体，不存在返回 null
     */
    Channel getById(String id);

    /** 按名称查询渠道路由。 */
    Channel getByName(String name);

    /** 查询全部渠道。 */
    List<Channel> listAll();

    /** 查询启用渠道：status=1 ORDER BY priority DESC, weight DESC。 */
    List<Channel> listEnabledOrdered();

    /**
     * 回写连通性测试结果（last_test_at / last_test_ok）。
     *
     * @param id         渠道 ID
     * @param ok         测试是否成功
     * @param lastTestAt 测试时间（ISO-8601 带偏移）
     */
    void updateLastTest(String id, boolean ok, String lastTestAt);

    /** 新增渠道 */
    void insert(Channel channel);

    /** 更新渠道全部配置。 */
    int update(Channel channel);

    /** 更新启用状态。 */
    int updateStatus(String id, Integer status, String updatedAt);

    /** 按 ID 删除渠道 */
    int deleteById(String id);
}
