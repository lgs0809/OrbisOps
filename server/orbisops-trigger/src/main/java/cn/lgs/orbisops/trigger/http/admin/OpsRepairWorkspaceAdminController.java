package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.OpsCodeDeliveryDTO;
import cn.lgs.orbisops.api.dto.OpsCodeDeliveryRequestDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.repair.CodeDeliveryApplicationService;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.trigger.application.repair.OpsCodeDeliveryMapper;
import cn.lgs.orbisops.trigger.application.repair.OpsRepairWorkspaceMapper;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ops/repair-workspaces")
public class OpsRepairWorkspaceAdminController {

    private final RepairWorkspaceApplicationService workspaces;
    private final CodeDeliveryApplicationService deliveries;
    private final OpsRepairWorkspaceMapper workspaceMapper;
    private final OpsCodeDeliveryMapper deliveryMapper;

    public OpsRepairWorkspaceAdminController(
            RepairWorkspaceApplicationService workspaces,
            CodeDeliveryApplicationService deliveries,
            OpsRepairWorkspaceMapper workspaceMapper,
            OpsCodeDeliveryMapper deliveryMapper) {
        this.workspaces = workspaces;
        this.deliveries = deliveries;
        this.workspaceMapper = workspaceMapper;
        this.deliveryMapper = deliveryMapper;
    }

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspace", workspaceMapper.capabilities(workspaces.capabilities()));
        result.put("delivery", deliveryMapper.capabilities(deliveries.capabilities()));
        return success(result);
    }

    @PostMapping
    public Response<OpsRepairWorkspaceDTO> create(
            @RequestBody OpsRepairWorkspaceRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("创建修复工作区失败", () -> workspaceMapper.view(
                workspaces.createAndVerify(workspaceMapper.candidate(request), actor(servletRequest))));
    }

    @GetMapping
    public Response<List<OpsRepairWorkspaceDTO>> list(
            @RequestParam(value = "projectId", required = false) String projectId) {
        return success(workspaceMapper.views(workspaces.list(projectId)));
    }

    @GetMapping("/{workspaceId}")
    public Response<OpsRepairWorkspaceDTO> detail(@PathVariable("workspaceId") String workspaceId) {
        return success(workspaceMapper.view(workspaces.get(workspaceId)));
    }

    @PostMapping("/{workspaceId}/cleanup")
    public Response<Map<String, Object>> cleanup(
            @PathVariable("workspaceId") String workspaceId,
            HttpServletRequest servletRequest) {
        return handle("清理修复工作区失败",
                () -> workspaceMapper.view(workspaces.cleanup(workspaceId, actor(servletRequest))));
    }

    @PostMapping("/{workspaceId}/deliveries")
    public Response<OpsCodeDeliveryDTO> publish(
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody(required = false) OpsCodeDeliveryRequestDTO request,
            HttpServletRequest servletRequest) {
        return handle("发布代码审查分支失败", () -> deliveryMapper.view(
                deliveries.publish(workspaceId, deliveryMapper.candidate(request), actor(servletRequest))));
    }

    @GetMapping("/{workspaceId}/deliveries")
    public Response<List<OpsCodeDeliveryDTO>> listDeliveries(
            @PathVariable("workspaceId") String workspaceId) {
        return success(deliveryMapper.views(deliveries.list(workspaceId)));
    }

    @PostMapping("/deliveries/{deliveryId}/refresh-ci")
    public Response<OpsCodeDeliveryDTO> refreshCi(
            @PathVariable("deliveryId") String deliveryId,
            HttpServletRequest servletRequest) {
        return handle("刷新 CI 状态失败", () -> deliveryMapper.view(
                deliveries.refreshCi(deliveryId, actor(servletRequest))));
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal.username();
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private <T> Response<T> handle(String message, Supplier<T> action) {
        try {
            return success(action.get());
        } catch (Exception e) {
            log.warn("{}：{}", message, e.getMessage());
            return Response.<T>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(message + "：" + e.getMessage())
                    .data(null)
                    .build();
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
