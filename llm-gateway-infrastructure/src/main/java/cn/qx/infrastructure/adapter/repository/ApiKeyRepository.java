package cn.qx.infrastructure.adapter.repository;

import cn.qx.domain.identity.adapter.repository.IApiKeyRepository;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.infrastructure.persistent.dao.ApiKeyMapper;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * API Key 仓储实现。
 */
@Repository
public class ApiKeyRepository implements IApiKeyRepository {

    /** API 密钥表 Mapper */
    @Resource
    private ApiKeyMapper apiKeyMapper;

    @Override
    public ApiKey getById(String id) {
        return apiKeyMapper.queryById(id);
    }

    @Override
    public List<ApiKey> listAll() {
        return apiKeyMapper.queryAll();
    }

    @Override
    public ApiKey authenticate(String key, String now) {
        return apiKeyMapper.queryAuthenticatable(key, now);
    }

    @Override
    public void insert(ApiKey apiKey) {
        try {
            apiKeyMapper.insert(apiKey);
        } catch (DuplicateKeyException e) {
            throw new AppException(ResponseCode.CONFLICT.getCode(), "API Key 已存在: " + apiKey.getKey(), e);
        }
    }

    @Override
    public int update(ApiKey apiKey) {
        try {
            return apiKeyMapper.update(apiKey);
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
}
