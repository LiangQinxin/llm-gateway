package cn.qx.infrastructure.persistent.dao;

import cn.qx.domain.channel.model.entity.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 渠道表 Mapper（基础版三表，ADR-0005）。
 * <p>实体即 PO（ADR-0004：PO 与实体同构免转换层），SQL 见 channel_mapper.xml。
 */
@Mapper
public interface ChannelMapper {

    /**
     * 按 ID 查询渠道。
     *
     * @param id 渠道 ID
     * @return 渠道实体，不存在返回 null
     */
    Channel queryById(@Param("id") String id);

    /** 按名称查询渠道。 */
    Channel queryByName(@Param("name") String name);

    /** 查询全部渠道。 */
    List<Channel> queryAll();

    /** 查询启用渠道并按路由顺序排序。 */
    List<Channel> queryEnabledOrdered();

    /** 新增渠道 */
    void insert(Channel channel);

    /** 更新渠道全部配置。 */
    int update(Channel channel);

    /** 更新启用状态。 */
    int updateStatus(@Param("id") String id,
                     @Param("status") Integer status,
                     @Param("updatedAt") String updatedAt);

    /**
     * 回写连通性测试结果。
     *
     * @param id         渠道 ID
     * @param ok         测试结果（1=成功，0=失败）
     * @param lastTestAt 测试时间（ISO-8601 带偏移）
     */
    void updateLastTest(@Param("id") String id, @Param("ok") Integer ok, @Param("lastTestAt") String lastTestAt);

    /**
     * 按 ID 删除渠道。
     *
     * @param id 渠道 ID
     */
    int deleteById(@Param("id") String id);
}
