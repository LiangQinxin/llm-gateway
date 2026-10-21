package cn.qx.api;

import cn.qx.domain.identity.model.entity.ApiKey;

import java.util.List;

/**
 * API Key 用例接口（api 根包，对齐「服务必须定义接口」约定）。
 */
public interface IApiKeyCase {

    /** 创建 API Key：ID、时间与缺省配置由用例生成。 */
    ApiKey create(ApiKey apiKey);

    /** 查询全部 API Key。 */
    List<ApiKey> list();

    /** 查询 API Key 详情，不存在抛 NOT_FOUND。 */
    ApiKey get(String id);

    /** 更新 API Key 全部配置。 */
    ApiKey update(ApiKey apiKey);

    /** 启用或停用 API Key。 */
    ApiKey toggle(String id, boolean enabled);

    /** 删除 API Key。 */
    void delete(String id);

    /** 认证调用方密钥，并校验模型/渠道白名单。 */
    ApiKey authenticate(String key, String model, String channelId);

    /** 请求完成后累加配额，SQL 原子封顶。 */
    ApiKey recordUsage(String id, long delta);
}
