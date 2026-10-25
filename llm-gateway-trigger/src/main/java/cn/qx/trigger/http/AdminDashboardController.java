package cn.qx.trigger.http;

import cn.qx.api.IDashboardCase;
import cn.qx.api.dto.dashboard.DashboardResult;
import cn.qx.api.response.Response;
import cn.qx.api.response.Responses;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台仪表盘 Controller（管理面 /admin/api/dashboard）。
 */
@RestController
@RequestMapping("/admin/api/dashboard")
public class AdminDashboardController {

    /** 仪表盘聚合用例 */
    @Resource
    private IDashboardCase dashboardCase;

    @GetMapping
    public Response<DashboardResult> dashboard() {
        return Responses.success(dashboardCase.dashboard());
    }
}
