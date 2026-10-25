package cn.qx.api.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理后台仪表盘聚合输出对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResult {

    /** 总请求数 */
    private long totalRequests;
    /** 总 Token 消耗 */
    private long totalTokens;
    /** 活跃渠道数（status=1） */
    private long activeChannels;
    /** 活跃密钥数（status=1） */
    private long activeApiKeys;
    /** 平均延迟（毫秒） */
    private long avgDurationMs;
    /** 错误次数（status_code &gt;= 400） */
    private long totalErrors;
    /** 健康分（0-100） */
    private int healthScore;
    /** 健康等级：excellent / good / warning / critical */
    private String healthBadge;
}
