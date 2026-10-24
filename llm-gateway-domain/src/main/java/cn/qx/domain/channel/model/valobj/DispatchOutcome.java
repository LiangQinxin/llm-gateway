package cn.qx.domain.channel.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道分发结果：未命中、非流式或流式三选一。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchOutcome {

    /** 是否已路由命中上游（true=已命中，false=未命中） */
    private boolean routed;
    /** 未命中/失败原因描述 */
    private String errorMessage;
    /** 未命中原因（routed=false 时携带） */
    private DispatchUnroutableReason reason;
    /** 命中或最后尝试的渠道 ID */
    private String channelId;
    /** 命中或最后尝试的渠道名称 */
    private String channelName;
    /** 最后一次实际烘焙的上游模型 */
    private String upstreamModel;
    /** 实际尝试渠道数 */
    private int attemptCount;
    /** 非流式转发结果（与 streaming 二选一） */
    private ForwardResult unary;
    /** 流式转发结果（与 unary 二选一） */
    private StreamResult streaming;

    public static DispatchOutcome notFound(String model) {
        return DispatchOutcome.builder()
                .routed(false)
                .reason(DispatchUnroutableReason.MODEL_NOT_FOUND)
                .errorMessage("The model '" + model + "' does not exist or you don't have access to it")
                .build();
    }

    public static DispatchOutcome noChannel(String model) {
        return DispatchOutcome.builder()
                .routed(false)
                .reason(DispatchUnroutableReason.NO_AVAILABLE_CHANNEL)
                .errorMessage("No enabled channel is available for model '" + model + "'")
                .build();
    }

    public static DispatchOutcome unary(ForwardResult result) {
        return DispatchOutcome.builder()
                .routed(true)
                .unary(result)
                .build();
    }

    public static DispatchOutcome streaming(StreamResult result) {
        return DispatchOutcome.builder()
                .routed(true)
                .streaming(result)
                .build();
    }
}
