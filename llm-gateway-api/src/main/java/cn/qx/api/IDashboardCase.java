package cn.qx.api;

import cn.qx.api.dto.dashboard.DashboardResult;

/**
 * 管理后台仪表盘聚合用例接口（api 根包，对齐「服务必须定义接口」约定）。
 */
public interface IDashboardCase {

    /** 聚合仪表盘统计：请求量、token、活跃渠道/密钥、延迟、错误与健康度。 */
    DashboardResult dashboard();
}
