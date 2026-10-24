package cn.qx.domain.proxy.model.valobj;

import cn.qx.domain.channel.model.valobj.TokenUsage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应终止时的审计快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamAuditSnapshot {

    /** 重建后的 choices JSON */
    private String responseChoices;
    /** 真实 usage；上游未返回时为空 */
    private TokenUsage usage;
    /** 原始 SSE 是否超过审计缓冲上限 */
    private boolean truncated;
}
