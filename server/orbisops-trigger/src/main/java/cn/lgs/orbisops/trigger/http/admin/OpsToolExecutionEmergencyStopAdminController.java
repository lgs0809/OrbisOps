package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionEmergencyStopPort;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Authenticated operator control for the durable project emergency stop. */
@RestController
@RequestMapping("/api/v1/admin/ops/tool-execution-emergency-stop")
public final class OpsToolExecutionEmergencyStopAdminController {

    private final ToolExecutionEmergencyStopPort control;

    public OpsToolExecutionEmergencyStopAdminController(
            ToolExecutionEmergencyStopPort control) {
        if (control == null) throw new IllegalArgumentException("TOOL_EXECUTION_EMERGENCY_STOP_CONTROL_REQUIRED");
        this.control = control;
    }

    @GetMapping
    public Response<ToolExecutionEmergencyStopPort.StopStatus> status(
            @RequestParam("projectId") String projectId) {
        return success(control.status(projectId));
    }

    @PostMapping
    public Response<ToolExecutionEmergencyStopPort.StopStatus> set(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        AdminAuthService.AuthPrincipal principal = principal(request);
        String projectId = required(safe.get("projectId"), "PROJECT_ID_REQUIRED");
        String reason = required(safe.get("reason"), "EMERGENCY_STOP_REASON_REQUIRED");
        boolean active = Boolean.TRUE.equals(safe.get("active"));
        control.set(projectId, active, reason, OpsTrustedRequestMetadata.actor(principal));
        return success(control.status(projectId));
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null
                ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal
                && AdminAuthService.SCOPE_ADMIN.equals(principal.scope())) return principal;
        throw new SecurityException("未获取到已认证管理员");
    }

    private String required(Object value, String reasonCode) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
