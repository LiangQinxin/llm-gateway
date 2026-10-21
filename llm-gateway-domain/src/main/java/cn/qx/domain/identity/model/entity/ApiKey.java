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

    /** API 密钥 ID（UUID） */
    private String id;
    /** 密钥名称 */
    private String name;
    /** 密钥明文（唯一） */
    private String key;
    /** 状态：1=启用，0=停用 */
    private Integer status;
    /** 允许模型白名单（JSON 数组字符串；空=全部允许） */
    private String allowedModels;
    /** 允许渠道白名单（JSON 数组字符串；空=全部允许） */
    private String allowedChannels;
    /** 额度上限 token 数（0=不限） */
    private Long quotaLimit;
    /** 已用额度 token 数 */
    private Long quotaUsed;
    /** 过期时间（ISO-8601 带偏移；空=永不过期） */
    private String expiresAt;
    /** 创建时间（ISO-8601 带偏移） */
    private String createdAt;
    /** 更新时间（ISO-8601 带偏移） */
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
