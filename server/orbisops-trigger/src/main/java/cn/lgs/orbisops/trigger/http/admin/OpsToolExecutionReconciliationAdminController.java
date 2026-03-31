package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionApplicationService;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.OpsTrustedRequestMetadata;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Manual reconciliation boundary for production ToolExecution results that are genuinely unknown. */
@RestController
@RequestMapping("/api/v1/admin/ops/tool-execution-reconciliation")
public class OpsToolExecutionReconciliationAdminController {

    private final ToolExecutionApplicationService toolExecution;
    private final OpsChangePackagePermissionService permissions;

    public OpsToolExecutionReconciliationAdminController(
            ToolExecutionApplicationService toolExecution,
            OpsChangePackagePermissionService permissions) {
        this.toolExecution = toolExecution;
        this.permissions = permissions;
    }

    @GetMapping("/unresolved")
    public Response<List<ToolExecutionIdempotencyPort.UnresolvedSideEffect>> unresolved(
            @RequestParam("projectId") String projectId,
            @RequestParam("runId") String runId,
            HttpServletRequest request) {
        AdminAuthService.AuthPrincipal principal = principal(request);
        permissions.assertCanLandPackage(required(projectId, "PROJECT_ID_REQUIRED"), principal);
        return success(toolExecution.unresolvedSideEffects(projectId, required(runId, "RUN_ID_REQUIRED")));
    }

    @PostMapping("/resolve")
    public Response<Map<String, Object>> resolve(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> safe = body == null ? Map.of() : body;
        String projectId = required(safe.get("projectId"), "PROJECT_ID_REQUIRED");
        String runId = required(safe.get("runId"), "RUN_ID_REQUIRED");
        AdminAuthService.AuthPrincipal principal = principal(request);
        permissions.assertCanLandPackage(projectId, principal);
        ToolExecutionIdempotencyPort.SideEffectResolution resolution;
        try {
            resolution = ToolExecutionIdempotencyPort.SideEffectResolution.valueOf(
                    required(safe.get("resolution"), "TOOL_EXECUTION_RESOLUTION_REQUIRED").toUpperCase());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "TOOL_EXECUTION_RESOLUTION_INVALID：仅支持 CONFIRMED_SUCCEEDED 或 CONFIRMED_NOT_EXECUTED",
                    error);
        }
        ToolExecutionIdempotencyPort.ResolveSideEffectCommand command =
                new ToolExecutionIdempotencyPort.ResolveSideEffectCommand(
                        required(safe.get("idempotencyKey"), "TOOL_IDEMPOTENCY_KEY_REQUIRED"),
                        projectId,
                        runId,
                        resolution,
                        required(safe.get("evidenceId"), "TOOL_EXECUTION_RECONCILIATION_EVIDENCE_REQUIRED"),
                        required(safe.get("evidenceHash"), "TOOL_EXECUTION_RECONCILIATION_EVIDENCE_HASH_REQUIRED"),
                        required(safe.get("note"), "TOOL_EXECUTION_RECONCILIATION_NOTE_REQUIRED"),
                        OpsTrustedRequestMetadata.actor(principal),
                        Instant.now());
        toolExecution.resolveUncertainSideEffect(command);
        return success(Map.of(
                "status", "RECONCILED",
                "projectId", projectId,
                "runId", runId,
                "idempotencyKey", command.idempotencyKey(),
                "resolution", resolution.name(),
                "remainingUnresolved", toolExecution.unresolvedSideEffects(projectId, runId).size()));
    }

    private AdminAuthService.AuthPrincipal principal(HttpServletRequest request) {
        Object value = request == null
                ? null
                : request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) return principal;
        throw new SecurityException("未获取到已认证管理员");
    }

    private String required(Object value, String error) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        if (!StringUtils.hasText(normalized)) throw new IllegalArgumentException(error);
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
