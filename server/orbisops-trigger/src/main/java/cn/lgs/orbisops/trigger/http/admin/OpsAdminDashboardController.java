package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.application.ops.OpsAdminDashboardApplicationService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class OpsAdminDashboardController {

    private final OpsAdminDashboardApplicationService dashboardApplicationService;

    public OpsAdminDashboardController(OpsAdminDashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @GetMapping("/overview")
    public Response<Map<String, Object>> overview() {
        try {
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(dashboardApplicationService.overview())
                    .build();
        } catch (Exception e) {
            log.warn("查询管理员工作台失败：{}", e.getMessage());
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info("查询管理员工作台失败：" + e.getMessage())
                    .data(Map.of())
                    .build();
        }
    }
}
