package cn.qx.api.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 启用/停用请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToggleRequest {

    private boolean enabled;
}
