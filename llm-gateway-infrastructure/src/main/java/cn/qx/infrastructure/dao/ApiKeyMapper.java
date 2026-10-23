package cn.qx.infrastructure.dao;

import cn.qx.infrastructure.dao.po.ApiKeyPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * api_keys 表 Mapper。SQL 见 api_key_mapper.xml。
 */
@Mapper
public interface ApiKeyMapper {

    ApiKeyPO queryById(@Param("id") String id);

    List<ApiKeyPO> queryAll();

    ApiKeyPO queryAuthenticatable(@Param("key") String key, @Param("now") String now);

    void insert(ApiKeyPO apiKeyPO);

    int update(ApiKeyPO apiKeyPO);

    int updateStatus(@Param("id") String id,
                     @Param("status") Integer status,
                     @Param("updatedAt") String updatedAt);

    int recordUsage(@Param("id") String id,
                    @Param("delta") long delta,
                    @Param("updatedAt") String updatedAt);

    int deleteById(@Param("id") String id);
}
