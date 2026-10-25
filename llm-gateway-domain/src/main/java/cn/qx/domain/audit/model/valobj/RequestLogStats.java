package cn.qx.domain.audit.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 请求日志聚合统计值对象（仪表盘等运营场景使用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestLogStats {

    /** 总请求条数 */
    private long totalRequests;
    /** 总 token 消耗 */
    private long totalTokens;
    /** 平均耗时（毫秒，向下取整） */
    private long avgDurationMs;
    /** 错误请求条数（status_code &gt;= 400） */
    private long totalErrors;
}
