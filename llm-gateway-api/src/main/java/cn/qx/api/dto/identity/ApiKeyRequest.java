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

    private String name;
    private String key;
    private Integer status;
    private String allowedModels;
    private String allowedChannels;
    private Long quotaLimit;
    private String expiresAt;
}
