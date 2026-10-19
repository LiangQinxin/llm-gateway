package cn.qx.domain.channel.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道连通性测试结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

    /** 是否连通成功 */
    private boolean success;

    /** 测试结果描述信息 */
    private String message;

    /** 探测延迟（毫秒） */
    private long latencyMs;
}
