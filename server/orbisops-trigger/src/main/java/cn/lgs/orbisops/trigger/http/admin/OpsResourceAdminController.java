package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.trigger.ops.OpsCapabilityReadinessService;
import cn.lgs.orbisops.trigger.ops.OpsResourceHealthService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运维资源健康和能力接口。
 */
@RestController
@RequestMapping("/api/v1/admin/ops")
public class OpsResourceAdminController {

    private final OpsResourceHealthService opsResourceHealthService;
    private final OpsCapabilityReadinessService capabilityReadinessService;

    public OpsResourceAdminController(OpsResourceHealthService opsResourceHealthService,
                                      OpsCapabilityReadinessService capabilityReadinessService) {
        this.opsResourceHealthService = opsResourceHealthService;
        this.capabilityReadinessService = capabilityReadinessService;
    }

    @GetMapping("/resources/health")
    public Response<Map<String, Object>> resourceHealth() {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsResourceHealthService.snapshot())
                .build();
    }

    @GetMapping("/resources/capabilities")
    public Response<List<Map<String, Object>>> resourceCapabilities() {
        return Response.<List<Map<String, Object>>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(opsResourceHealthService.capabilities())
                .build();
    }

    @GetMapping("/resources/readiness")
    public Response<Map<String, Object>> capabilityReadiness() {
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(capabilityReadinessService.snapshot())
                .build();
    }
}
