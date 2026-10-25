package cn.qx.cases.audit;

import cn.qx.api.IApiKeyCase;
import cn.qx.api.IChannelCase;
import cn.qx.api.IDashboardCase;
import cn.qx.api.IRequestLogCase;
import cn.qx.api.dto.dashboard.DashboardResult;
import cn.qx.domain.audit.model.valobj.RequestLogStats;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.identity.model.entity.ApiKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理后台仪表盘聚合用例。
 * <p>数据来源：请求日志聚合统计 + 渠道/密钥启用状态，健康度按错误率与延迟评分。
 */
@Slf4j
@Service
public class DashboardCase implements IDashboardCase {

    /** 请求日志用例（聚合统计） */
    @Resource
    private IRequestLogCase requestLogCase;

    /** 渠道用例（活跃渠道统计） */
    @Resource
    private IChannelCase channelCase;

    /** 密钥用例（活跃密钥统计） */
    @Resource
    private IApiKeyCase apiKeyCase;

    @Override
    public DashboardResult dashboard() {
        RequestLogStats stats = requestLogCase.stats();
        List<Channel> channels = channelCase.list();
        List<ApiKey> apiKeys = apiKeyCase.list();

        long activeChannels = channels.stream().filter(c -> c.getStatus() != null && c.getStatus() == 1).count();
        long activeApiKeys = apiKeys.stream().filter(k -> k.getStatus() != null && k.getStatus() == 1).count();
        long avgDurationMs = stats.getAvgDurationMs();

        int healthScore = score(stats, avgDurationMs, activeChannels);
        return DashboardResult.builder()
                .totalRequests(stats.getTotalRequests())
                .totalTokens(stats.getTotalTokens())
                .activeChannels(activeChannels)
                .activeApiKeys(activeApiKeys)
                .avgDurationMs(avgDurationMs)
                .totalErrors(stats.getTotalErrors())
                .healthScore(healthScore)
                .healthBadge(badge(healthScore))
                .build();
    }

    /** 健康评分：基础 100，错误率每 1% 扣 2 分（上限 30），平均延迟超 2s 扣 10，无活跃渠道扣 30。 */
    private static int score(RequestLogStats stats, long avgDurationMs, long activeChannels) {
        int score = 100;
        if (stats.getTotalRequests() > 0) {
            double errRate = (double) stats.getTotalErrors() / stats.getTotalRequests();
            score -= (int) Math.min(30, errRate * 200);
        }
        if (avgDurationMs > 2000) {
            score -= 10;
        }
        if (activeChannels == 0) {
            score -= 30;
        }
        return Math.max(0, Math.min(100, score));
    }

    private static String badge(int score) {
        if (score >= 90) {
            return "excellent";
        }
        if (score >= 75) {
            return "good";
        }
        if (score >= 60) {
            return "warning";
        }
        return "critical";
    }
}
