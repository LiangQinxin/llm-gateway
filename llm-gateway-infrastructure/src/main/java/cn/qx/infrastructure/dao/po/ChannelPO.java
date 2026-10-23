package cn.qx.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * channels 表持久化对象（PO），字段与库表列一一对应。
 * <p>列名 → 属性名：id、name、type、base_url→baseUrl、api_key→apiKey、models、
 * status、priority、weight、config、model_mapping→modelMapping、last_test_at→lastTestAt、
 * last_test_ok→lastTestOk、created_at→createdAt、updated_at→updatedAt。
 * JSON 列（models / config / model_mapping）保持 String，按需在领域层解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPO {

    /** id 列：渠道 ID（UUID） */
    private String id;
    /** name 列：渠道名称 */
    private String name;
    /** type 列：渠道类型（openai/anthropic/custom） */
    private String type;
    /** base_url 列：上游基础 URL */
    private String baseUrl;
    /** api_key 列：上游 API 密钥 */
    private String apiKey;
    /** models 列：支持的模型列表（JSON 数组字符串） */
    private String models;
    /** status 列：1=启用，0=停用 */
    private Integer status;
    /** priority 列：优先级（数值越大优先级越高） */
    private Integer priority;
    /** weight 列：权重（同优先级内按权重分发） */
    private Integer weight;
    /** config 列：渠道额外配置（JSON 字符串） */
    private String config;
    /** model_mapping 列：模型名映射（JSON 字符串） */
    private String modelMapping;
    /** last_test_at 列：最近一次连通性测试时间（ISO-8601 带偏移） */
    private String lastTestAt;
    /** last_test_ok 列：最近一次连通性测试结果：1=成功，0=失败 */
    private Integer lastTestOk;
    /** created_at 列：创建时间（ISO-8601 带偏移） */
    private String createdAt;
    /** updated_at 列：更新时间（ISO-8601 带偏移） */
    private String updatedAt;
}
