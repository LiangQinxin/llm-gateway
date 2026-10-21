package cn.qx.api;

import cn.qx.api.dto.channel.ModelListResult;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.TestResult;

import java.util.List;

/**
 * 渠道用例接口（api 根包，对齐「服务必须定义接口」约定）。
 */
public interface IChannelCase {

    /** 新建渠道：ID、时间与缺省配置由用例生成。 */
    Channel create(Channel channel);

    /** 查询全部渠道。 */
    List<Channel> list();

    /** 查询启用渠道并按路由顺序排序。 */
    List<Channel> listEnabledOrdered();

    /** 查询渠道详情。 */
    Channel get(String id);

    /** 更新渠道全部配置。 */
    Channel update(Channel channel);

    /** 启用或停用渠道。 */
    Channel toggle(String id, boolean enabled);

    /** 删除渠道；不存在视为幂等成功。 */
    void delete(String id);

    /** 渠道连通性测试（真实上游端点）。 */
    TestResult testChannel(String id);

    /** 聚合启用渠道的模型目录。 */
    ModelListResult listModelCatalog();
}
