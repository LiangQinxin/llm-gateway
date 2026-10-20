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

    private boolean routed;
    private String errorMessage;
    private ForwardResult unary;
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
