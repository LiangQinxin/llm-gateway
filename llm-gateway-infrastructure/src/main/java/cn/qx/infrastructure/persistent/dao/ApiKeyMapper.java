package cn.qx.infrastructure.persistent.dao;

import cn.qx.domain.identity.model.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * api_keys 表 Mapper。
 */
@Mapper
public interface ApiKeyMapper {

    ApiKey queryById(@Param("id") String id);

    List<ApiKey> queryAll();

    ApiKey queryAuthenticatable(@Param("key") String key, @Param("now") String now);

    void insert(ApiKey apiKey);

    int update(ApiKey apiKey);

    int updateStatus(@Param("id") String id,
                     @Param("status") Integer status,
                     @Param("updatedAt") String updatedAt);

    int recordUsage(@Param("id") String id,
                    @Param("delta") long delta,
                    @Param("updatedAt") String updatedAt);

    int deleteById(@Param("id") String id);
}
