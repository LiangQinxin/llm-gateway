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

    /** 渠道名称 */
    private String name;
    /** 渠道类型：deepseek / openai / claude / gemini / custom（协议级标识） */
    private String type;
    /** 上游基础 URL */
    private String baseUrl;
    /** 上游 API 密钥 */
    private String apiKey;
    /** 启用模型列表（JSON 数组字符串，如 ["deepseek-chat","deepseek-flash"]） */
    private String models;
    /** 状态：1=启用，0=停用 */
    private Integer status;
    /** 优先级（数值越大优先级越高；路由按 priority DESC, weight DESC 排序） */
    private Integer priority;
    /** 权重（同优先级内按权重分发） */
    private Integer weight;
    /** 额外配置 JSON 字符串（超时时间等扩展键） */
    private String config;
    /** 模型映射 JSON 字符串 */
    private String modelMapping;
}
