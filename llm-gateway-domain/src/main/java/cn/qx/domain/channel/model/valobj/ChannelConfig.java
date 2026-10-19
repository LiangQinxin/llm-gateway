package cn.qx.domain.channel.model.valobj;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 渠道配置——适配器消费的渠道参数视图。
 * <p>与数据库 Channel 实体分离：适配器不关心存储细节（ADR-0004：JSON 列实体保持 String + 便捷解析）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelConfig {

    /** 上游基础 URL（含 /v1，转发时拼接 /chat/completions，连通性测试时拼接 /models） */
    private String baseUrl;

    /** 上游 API 密钥 */
    private String apiKey;

    /** 渠道支持/已配置的模型列表 */
    private List<String> models;

    /** 模型映射：key=下游请求模型名，value=上游真实模型名（字符串映射或数组采样目标） */
    private JSONObject modelMapping;

    /** 渠道额外配置（超时时间等扩展键） */
    private JSONObject extra;

    /** 请求总超时（秒），缺省 300 */
    private long timeoutSecs;
}
