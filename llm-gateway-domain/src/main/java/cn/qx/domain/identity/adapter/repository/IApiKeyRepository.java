package cn.qx.domain.identity.adapter.repository;

import cn.qx.domain.identity.model.entity.ApiKey;

import java.util.List;

/**
 * API Key 仓储端口。
 */
public interface IApiKeyRepository {

    ApiKey getById(String id);

    List<ApiKey> listAll();

    /** key 精确匹配 + status=1 + 未过期。 */
    ApiKey authenticate(String key, String now);

    void insert(ApiKey apiKey);

    int update(ApiKey apiKey);

    int updateStatus(String id, Integer status, String updatedAt);

    /** 原子累加并封顶；quota_limit=0 时不封顶。 */
    int recordUsage(String id, long delta, String updatedAt);

    int deleteById(String id);
}
