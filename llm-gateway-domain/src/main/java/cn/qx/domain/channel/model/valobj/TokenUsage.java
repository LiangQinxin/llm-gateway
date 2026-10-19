package cn.qx.domain.channel.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 用量——统一各家上游的计费格式。
 * <p>由各适配器负责从上游响应转换为统一格式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    /** 提示词 Token 数 */
    private long promptTokens;

    /** 补全 Token 数 */
    private long completionTokens;

    /** 总 Token 数 */
    private long totalTokens;
}
