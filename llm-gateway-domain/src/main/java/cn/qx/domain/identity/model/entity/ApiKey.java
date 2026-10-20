package cn.qx.domain.identity.model.entity;

import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 调用方 API Key 实体，对应 api_keys 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    private String id;
    private String name;
    private String key;
    private Integer status;
    private String allowedModels;
    private String allowedChannels;
    private Long quotaLimit;
    private Long quotaUsed;
    private String expiresAt;
    private String createdAt;
    private String updatedAt;

    /** 解析模型白名单；空数组表示全部允许。 */
    public List<String> activeAllowedModels() {
        return parseArray(allowedModels);
    }

    /** 解析渠道白名单；空数组表示全部允许。 */
    public List<String> activeAllowedChannels() {
        return parseArray(allowedChannels);
    }

    /** 模型是否在白名单内；空白名单全放行。 */
    public boolean allowsModel(String model) {
        List<String> allowed = activeAllowedModels();
        return allowed.isEmpty() || allowed.contains(model);
    }

    /** 渠道是否在白名单内；空白名单全放行。 */
    public boolean allowsChannel(String channelId) {
        List<String> allowed = activeAllowedChannels();
        return allowed.isEmpty() || allowed.contains(channelId);
    }

    private static List<String> parseArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, String.class);
    }
}
