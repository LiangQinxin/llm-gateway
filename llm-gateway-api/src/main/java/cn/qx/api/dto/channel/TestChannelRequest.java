package cn.qx.api.dto.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道连通性测试请求体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestChannelRequest {

    /** 渠道 ID */
    private String id;
}
