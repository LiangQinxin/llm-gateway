package cn.qx.api.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建/更新 API Key 请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyRequest {

    /** 密钥名称 */
    private String name;

    /** API Key 密钥串（如 sk-xxx） */
    private String key;

    /** 状态：1=启用，0=停用（缺省 1=启用） */
    private Integer status;

    /** 允许调用的模型白名单（JSON 数组字符串，为空=全部允许） */
    private String allowedModels;

    /** 允许使用的渠道白名单（JSON 数组字符串，为空=全部允许） */
    private String allowedChannels;

    /** 配额上限（单位：次；0=不限，缺省 0） */
    private Long quotaLimit;

    /** 过期时间（ISO-8601 带偏移；为空=永不过期） */
    private String expiresAt;
}
