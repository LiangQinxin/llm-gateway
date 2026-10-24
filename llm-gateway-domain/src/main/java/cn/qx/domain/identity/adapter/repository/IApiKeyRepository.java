package cn.qx.domain.identity.adapter.repository;

import cn.qx.domain.identity.model.entity.ApiKey;

import java.util.List;

/**
 * API Key 仓储端口。
 */
public interface IApiKeyRepository {

    /** 按 ID 查询 API Key，不存在返回 null。 */
    ApiKey getById(String id);

    /** 查询全部 API Key。 */
    List<ApiKey> listAll();

    /** key 精确匹配 + status=1 + 未过期。 */
    ApiKey authenticate(String key, String now);

    /** 新增 API Key。 */
    void insert(ApiKey apiKey);

    /** 更新 API Key 全部字段，返回影响行数。 */
    int update(ApiKey apiKey);

    /** 仅更新启用状态与更新时间，返回影响行数。 */
    int updateStatus(String id, Integer status, String updatedAt);

    /** 原子累加并封顶；quota_limit=0 时不封顶。 */
    int recordUsage(String id, long delta, String updatedAt);

    /** 按 ID 删除 API Key，返回影响行数。 */
    int deleteById(String id);
}
