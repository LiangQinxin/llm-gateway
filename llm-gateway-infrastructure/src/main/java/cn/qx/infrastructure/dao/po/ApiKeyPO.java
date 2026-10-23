package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * api_keys 表持久化对象（PO），字段与库表列一一对应。
 * <p>列名 → 属性名：id、name、`key`、status、allowed_models→allowedModels、
 * allowed_channels→allowedChannels、quota_limit→quotaLimit、quota_used→quotaUsed、
 * expires_at→expiresAt、created_at→createdAt、updated_at→updatedAt。
 * JSON 列（allowed_models / allowed_channels）保持 String，按需在领域层解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyPO {

    /** id 列：API 密钥 ID（UUID） */
    private String id;
    /** name 列：密钥名称 */
    private String name;
    /** `key` 列：密钥明文（唯一） */
    private String key;
    /** status 列：1=启用，0=停用 */
    private Integer status;
    /** allowed_models 列：允许模型白名单（JSON 数组字符串；空=全部允许） */
    private String allowedModels;
    /** allowed_channels 列：允许渠道白名单（JSON 数组字符串；空=全部允许） */
    private String allowedChannels;
    /** quota_limit 列：额度上限 token 数（0=不限） */
    private Long quotaLimit;
    /** quota_used 列：已用额度 token 数 */
    private Long quotaUsed;
    /** expires_at 列：过期时间（ISO-8601 带偏移；空=永不过期） */
    private String expiresAt;
    /** created_at 列：创建时间（ISO-8601 带偏移） */
    private String createdAt;
    /** updated_at 列：更新时间（ISO-8601 带偏移） */
    private String updatedAt;
}
