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

    /** 待扫描的请求/响应原文（JSON 或纯文本）。 */
    private String body;

    /** 扫描方向，默认请求体 REQUEST。 */
    @Builder.Default
    private ScanPhase phase = ScanPhase.REQUEST;

    /** 扫描配置；为空时用引擎默认配置。 */
    private ScanSettings settings;

    public static ScanRequest of(String body, ScanSettings settings) {
        return ScanRequest.builder()
                .body(body)
                .phase(ScanPhase.REQUEST)
                .settings(settings)
                .build();
    }
}
