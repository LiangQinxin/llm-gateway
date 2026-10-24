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

    /** 按主键 id 查询 API Key。 */
    ApiKeyPO queryById(@Param("id") String id);

    /** 查询全部 API Key，按创建时间倒序。 */
    List<ApiKeyPO> queryAll();

    /** 按密钥精确匹配（BINARY 大小写敏感）查询可认证的 Key：status=1 且未过期，LIMIT 1。 */
    ApiKeyPO queryAuthenticatable(@Param("key") String key, @Param("now") String now);

    /** 新增一条 API Key。 */
    void insert(ApiKeyPO apiKeyPO);

    /** 按主键更新 API Key 全字段（id 为条件），返回影响行数。 */
    int update(ApiKeyPO apiKeyPO);

    /** 仅更新启用状态与更新时间，返回影响行数。 */
    int updateStatus(@Param("id") String id,
                     @Param("status") Integer status,
                     @Param("updatedAt") String updatedAt);

    /** 原子累加已用额度：quota_limit>0 时 LEAST 封顶，否则不封顶；返回影响行数。 */
    int recordUsage(@Param("id") String id,
                    @Param("delta") long delta,
                    @Param("updatedAt") String updatedAt);

    /** 按主键删除 API Key，返回影响行数。 */
    int deleteById(@Param("id") String id);
}
