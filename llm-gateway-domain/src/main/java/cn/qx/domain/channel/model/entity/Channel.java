package cn.qx.domain.channel.model.entity;

import cn.qx.domain.channel.model.valobj.ChannelConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 渠道实体——对应 channels 表（基础版三表，ADR-0005）。
 * <ul>
 *   <li>时间 / ID 全 String（ISO-8601 带偏移 + UUID，ADR-0004）；</li>
 *   <li>JSON 列实体保持 String + 便捷解析；</li>
 *   <li>PO 与实体同构免转换层（MyBatis 直接映射本实体）。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Channel {

    /** 渠道 ID（UUID） */
    private String id;

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

    /** 优先级（数值越小优先级越高） */
    private Integer priority;

    /** 权重（同优先级内按权重分发） */
    private Integer weight;

    /** 额外配置 JSON 字符串（超时时间等扩展键） */
    private String config;

    /** 模型映射 JSON 字符串 */
    private String modelMapping;

    /** 最近一次连通性测试时间（ISO-8601 带偏移） */
    private String lastTestAt;

    /** 最近一次连通性测试结果：1=成功，0=失败 */
    private Integer lastTestOk;

    /** 创建时间（ISO-8601 带偏移） */
    private String createdAt;

    /** 更新时间（ISO-8601 带偏移） */
    private String updatedAt;

    /** 便捷解析：启用模型列表 */
    public List<String> activeModels() {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }
        return JSON.parseArray(models, String.class);
    }

    /** 便捷解析：模型映射 */
    public JSONObject activeModelMapping() {
        if (modelMapping == null || modelMapping.isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(modelMapping);
    }

    /** 便捷解析：额外配置 */
    public JSONObject activeConfig() {
        if (config == null || config.isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(config);
    }

    /**
     * 转换：Channel → ChannelConfig（适配器消费的参数视图）。
     * <p>路由阶段复用；timeout_secs 从 config JSON 读取，缺省 300。
     *
     * @return 渠道配置视图
     */
    public ChannelConfig toChannelConfig() {
        JSONObject extra = activeConfig();
        long timeoutSecs = extra.getLongValue("timeout_secs", 300L);
        return ChannelConfig.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .models(activeModels())
                .modelMapping(activeModelMapping())
                .extra(extra)
                .timeoutSecs(timeoutSecs)
                .build();
    }
}
