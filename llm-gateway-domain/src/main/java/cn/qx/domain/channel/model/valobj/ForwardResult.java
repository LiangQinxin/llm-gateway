package cn.qx.domain.channel.model.valobj;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非流式转发结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwardResult {

    /** 上游 HTTP 状态码 */
    private int statusCode;

    /** 上游响应体（OpenAI 格式 JSON） */
    private JSONObject body;

    /** 上游原始响应文本，字节保真透传使用。 */
    private String raw;

    /** Token 用量（上游未返回 usage 时为 null） */
    private TokenUsage usage;
}
