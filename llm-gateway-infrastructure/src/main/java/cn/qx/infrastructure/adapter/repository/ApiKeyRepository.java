package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.identity.adapter.repository.IApiKeyRepository;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.infrastructure.dao.ApiKeyMapper;
import cn.qx.infrastructure.dao.po.ApiKeyPO;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * API Key 仓储实现——MyBatis 读写 api_keys 表。
 * <p>PO（ApiKeyPO）与领域实体（ApiKey）字段同构，出入参在此转换。
 */
@Repository
public class ApiKeyRepository implements IApiKeyRepository {

    /** API 密钥表 Mapper */
    @Resource
    private ApiKeyMapper apiKeyMapper;

    @Override
    public ApiKey getById(String id) {
        return toEntity(apiKeyMapper.queryById(id));
    }

    @Override
    public List<ApiKey> listAll() {
        return apiKeyMapper.queryAll().stream().map(ApiKeyRepository::toEntity).toList();
    }

    @Override
    public ApiKey authenticate(String key, String now) {
        return toEntity(apiKeyMapper.queryAuthenticatable(key, now));
    }

    @Override
    public void insert(ApiKey apiKey) {
        try {
            apiKeyMapper.insert(toPO(apiKey));
        } catch (DuplicateKeyException e) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "API Key 已存在: " + apiKey.getKey(), e);
        }
    }

    @Override
    public int update(ApiKey apiKey) {
        try {
            return apiKeyMapper.update(toPO(apiKey));
        } catch (DuplicateKeyException e) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "API Key 已存在: " + apiKey.getKey(), e);
        }
    }

    @Override
    public int updateStatus(String id, Integer status, String updatedAt) {
        return apiKeyMapper.updateStatus(id, status, updatedAt);
    }

    @Override
    public int recordUsage(String id, long delta, String updatedAt) {
        return apiKeyMapper.recordUsage(id, delta, updatedAt);
    }

    @Override
    public int deleteById(String id) {
        return apiKeyMapper.deleteById(id);
    }

    private static ApiKeyPO toPO(ApiKey entity) {
        ApiKeyPO po = new ApiKeyPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    private static ApiKey toEntity(ApiKeyPO po) {
        if (po == null) {
            return null;
        }
        ApiKey entity = new ApiKey();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }
}
