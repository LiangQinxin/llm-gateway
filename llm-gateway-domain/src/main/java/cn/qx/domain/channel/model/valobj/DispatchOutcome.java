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
    /** 非流式转发结果（与 streaming 二选一） */
    private ForwardResult unary;
    /** 流式转发结果（与 unary 二选一） */
    private StreamResult streaming;

    public static DispatchOutcome notFound(String model) {
        return DispatchOutcome.builder()
                .routed(false)
                .errorMessage("The model '" + model + "' does not exist or you don't have access to it")
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
