package cn.qx.infrastructure.dao;

import cn.qx.infrastructure.dao.po.ChannelPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * channels 表 Mapper（基础版三表，ADR-0005）。SQL 见 channel_mapper.xml。
 * <p>持久化对象为 ChannelPO（cn.qx.infrastructure.dao.po），字段与库表列一致。
 */
@Mapper
public interface ChannelMapper {

    /**
     * 按 ID 查询渠道。
     *
     * @param id 渠道 ID
     * @return 渠道 PO，不存在返回 null
     */
    ChannelPO queryById(@Param("id") String id);

    /** 按名称查询渠道。 */
    ChannelPO queryByName(@Param("name") String name);

    /** 查询全部渠道。 */
    List<ChannelPO> queryAll();

    /** 查询启用渠道并按路由顺序排序。 */
    List<ChannelPO> queryEnabledOrdered();

    /** 新增渠道 */
    void insert(ChannelPO channelPO);

    /** 更新渠道全部配置。 */
    int update(ChannelPO channelPO);

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
