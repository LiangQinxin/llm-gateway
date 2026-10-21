package cn.qx.cases.identity;

import cn.qx.api.IApiKeyCase;
import cn.qx.domain.identity.adapter.repository.IApiKeyRepository;
import cn.qx.domain.identity.model.entity.ApiKey;
import cn.qx.types.common.TimeUtils;
import cn.qx.types.enums.ResponseCode;
import cn.qx.types.exception.AppException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * API Key 用例：生命周期、鉴权、白名单与配额。
 */
@Slf4j
@Service
public class ApiKeyCase implements IApiKeyCase {

    /** API Key 仓储端口 */
    @Resource
    private IApiKeyRepository apiKeyRepository;

    @Override
    public ApiKey create(ApiKey apiKey) {
        if (apiKey == null || isBlank(apiKey.getName())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "密钥名称不能为空");
        }
        String now = TimeUtils.now();
        apiKey.setId(isBlank(apiKey.getId()) ? TimeUtils.uuid() : apiKey.getId());
        apiKey.setKey(isBlank(apiKey.getKey()) ? "sk-waliapi-" + TimeUtils.uuid() : apiKey.getKey());
        apiKey.setStatus(apiKey.getStatus() == null ? 1 : apiKey.getStatus());
        apiKey.setAllowedModels(defaultJson(apiKey.getAllowedModels()));
        apiKey.setAllowedChannels(defaultJson(apiKey.getAllowedChannels()));
        apiKey.setQuotaLimit(apiKey.getQuotaLimit() == null ? 0L : apiKey.getQuotaLimit());
        apiKey.setQuotaUsed(apiKey.getQuotaUsed() == null ? 0L : apiKey.getQuotaUsed());
        apiKey.setExpiresAt(TimeUtils.normalize(apiKey.getExpiresAt()));
        apiKey.setCreatedAt(now);
        apiKey.setUpdatedAt(now);
        apiKeyRepository.insert(apiKey);
        log.info("API Key 已创建: {}", JSON.toJSONString(apiKey));
        return apiKey;
    }

    @Override
    public List<ApiKey> list() {
        return apiKeyRepository.listAll();
    }

    @Override
    public ApiKey get(String id) {
        ApiKey apiKey = apiKeyRepository.getById(id);
        if (apiKey == null) {
            throw new AppException(ResponseCode.NOT_FOUND.getCode(), "API Key 不存在: " + id);
        }
        return apiKey;
    }

    @Override
    public ApiKey update(ApiKey apiKey) {
        if (apiKey == null || isBlank(apiKey.getId())) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "API Key ID 不能为空");
        }
        ApiKey current = get(apiKey.getId());
        apiKey.setKey(isBlank(apiKey.getKey()) ? current.getKey() : apiKey.getKey());
        apiKey.setStatus(apiKey.getStatus() == null ? current.getStatus() : apiKey.getStatus());
        apiKey.setAllowedModels(defaultJson(apiKey.getAllowedModels()));
        apiKey.setAllowedChannels(defaultJson(apiKey.getAllowedChannels()));
        apiKey.setQuotaLimit(apiKey.getQuotaLimit() == null ? current.getQuotaLimit() : apiKey.getQuotaLimit());
        apiKey.setQuotaUsed(apiKey.getQuotaUsed() == null ? current.getQuotaUsed() : apiKey.getQuotaUsed());
        apiKey.setExpiresAt(isBlank(apiKey.getExpiresAt())
                ? current.getExpiresAt()
                : TimeUtils.normalize(apiKey.getExpiresAt()));
        apiKey.setCreatedAt(current.getCreatedAt());
        apiKey.setUpdatedAt(TimeUtils.now());
        apiKeyRepository.update(apiKey);
        log.info("API Key 已更新: {}", JSON.toJSONString(apiKey));
        return apiKey;
    }

    @Override
    public ApiKey toggle(String id, boolean enabled) {
        get(id);
        apiKeyRepository.updateStatus(id, enabled ? 1 : 0, TimeUtils.now());
        return get(id);
    }

    @Override
    public void delete(String id) {
        apiKeyRepository.deleteById(id);
    }

    /** 认证调用方密钥，并校验模型/渠道白名单。 */
    @Override
    public ApiKey authenticate(String key, String model, String channelId) {
        if (isBlank(key)) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "缺少 API Key");
        }
        ApiKey apiKey = apiKeyRepository.authenticate(key, TimeUtils.now());
        if (apiKey == null) {
            throw new AppException(ResponseCode.UNAUTHORIZED.getCode(), "API Key 无效、已禁用或已过期");
        }
        if (!apiKey.allowsModel(model) || !apiKey.allowsChannel(channelId)) {
            throw new AppException(ResponseCode.FORBIDDEN.getCode(), "API Key 无权访问该模型或渠道");
        }
        return apiKey;
    }

    /** 请求完成后累加配额，SQL 原子封顶。 */
    @Override
    public ApiKey recordUsage(String id, long delta) {
        if (delta < 0) {
            throw new AppException(ResponseCode.ILLEGAL_PARAMETER.getCode(), "配额增量不能为负数");
        }
        get(id);
        apiKeyRepository.recordUsage(id, delta, TimeUtils.now());
        return get(id);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultJson(String value) {
        return isBlank(value) ? "[]" : value;
    }
}
