package cn.qx.api.dto.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建/更新渠道请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelRequest {

    private String name;
    private String type;
    private String baseUrl;
    private String apiKey;
    private String models;
    private Integer status;
    private Integer priority;
    private Integer weight;
    private String config;
    private String modelMapping;
}
