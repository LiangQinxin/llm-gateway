package cn.qx.api.dto.channel;

import cn.qx.domain.channel.model.entity.Channel;
import com.alibaba.fastjson2.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 渠道管理面输出对象。
 * <p>与前端约定：models 返回字符串数组（领域实体为 JSON 字符串），其余字段透传。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelView {

    /** 渠道 ID（UUID） */
    private String id;

    /** 渠道名称 */
    private String name;

    /** 渠道类型：deepseek / openai / claude / gemini / custom */
    private String type;

    /** 上游基础 URL */
    private String baseUrl;

    /** 上游 API 密钥 */
    private String apiKey;

    /** 启用模型列表 */
    private List<String> models;

    /** 状态：1=启用，0=停用 */
    private Integer status;

    /** 优先级 */
    private Integer priority;

    /** 权重 */
    private Integer weight;

    /** 额外配置 JSON 字符串 */
    private String config;

    /** 模型映射 JSON 字符串 */
    private String modelMapping;

    /** 最近一次连通性测试时间 */
    private String lastTestAt;

    /** 最近一次连通性测试结果：1=成功，0=失败 */
    private Integer lastTestOk;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

    /** 领域实体 → 管理面输出对象。 */
    public static ChannelView from(Channel channel) {
        if (channel == null) {
            return null;
        }
        return ChannelView.builder()
                .id(channel.getId())
                .name(channel.getName())
                .type(channel.getType())
                .baseUrl(channel.getBaseUrl())
                .apiKey(channel.getApiKey())
                .models(parseModels(channel.getModels()))
                .status(channel.getStatus())
                .priority(channel.getPriority())
                .weight(channel.getWeight())
                .config(channel.getConfig())
                .modelMapping(channel.getModelMapping())
                .lastTestAt(channel.getLastTestAt())
                .lastTestOk(channel.getLastTestOk())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }

    private static List<String> parseModels(String models) {
        if (models == null || models.isBlank()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(models, String.class);
    }
}
