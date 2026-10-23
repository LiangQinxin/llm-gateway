package cn.qx.domain.security.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一次安全扫描的输入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {

    private String body;

    @Builder.Default
    private ScanPhase phase = ScanPhase.REQUEST;

    private ScanSettings settings;

    public static ScanRequest of(String body, ScanSettings settings) {
        return ScanRequest.builder()
                .body(body)
                .phase(ScanPhase.REQUEST)
                .settings(settings)
                .build();
    }
}
