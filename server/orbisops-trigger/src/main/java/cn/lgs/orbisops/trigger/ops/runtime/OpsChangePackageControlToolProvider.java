package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.change.OpsChangePackagePermissionService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Atomic ChangePackage control tools used by the normal Daily Agent. */
@Service
public final class OpsChangePackageControlToolProvider {

    public static final String APPROVE_TOOL = "ApproveChangePackage";
    public static final String REJECT_TOOL = "RejectChangePackage";
    public static final String START_LANDING_TOOL = "StartChangePackageLanding";

    private final OpsToolExecutionService toolExecutionService;
    private final OpsChangePackagePermissionService permissionService;

    public OpsChangePackageControlToolProvider(
            OpsToolExecutionService toolExecutionService,
            OpsChangePackagePermissionService permissionService) {
        if (toolExecutionService == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_SERVICE_REQUIRED");
        }
        if (permissionService == null) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_PERMISSION_SERVICE_REQUIRED");
        }
        this.toolExecutionService = toolExecutionService;
        this.permissionService = permissionService;
    }

    public List<ToolCallback> build(
            String projectId,
            String actor,
            String runId,
            AdminAuthService.AuthPrincipal principal) {
        requireContext(projectId, actor, runId);
        if (principal == null) throw new SecurityException("TRUSTED_AUTH_PRINCIPAL_REQUIRED");
        return List.of(
                approve(projectId, actor, runId, principal),
                reject(projectId, actor, runId, principal),
                startLanding(projectId, actor, runId, principal));
    }

    private ToolCallback approve(
            String projectId,
            String actor,
            String runId,
            AdminAuthService.AuthPrincipal principal) {
        Function<ApproveInput, String> function = input -> {
            permissionService.assertCanApprovePackage(projectId, principal);
            return JSON.toJSONString(execute(
                    projectId,
                    actor,
                    runId,
                    "change_package_approve",
                    approveArguments(input, principal)));
        };
        return FunctionToolCallback.builder(APPROVE_TOOL, function)
                .description("Approve exactly one ChangePackage version. packageId, positive version and exact packageHash are required; approval does not execute production changes.")
                .inputType(ApproveInput.class)
                .build();
    }

    private ToolCallback reject(
            String projectId,
            String actor,
            String runId,
            AdminAuthService.AuthPrincipal principal) {
        Function<RejectInput, String> function = input -> {
            permissionService.assertCanRejectPackage(projectId, principal);
            return JSON.toJSONString(execute(
                    projectId,
                    actor,
                    runId,
                    "change_package_reject",
                    rejectArguments(input)));
        };
        return FunctionToolCallback.builder(REJECT_TOOL, function)
                .description("Reject a ChangePackage. This is a deterministic review-state transition and never executes production changes.")
                .inputType(RejectInput.class)
                .build();
    }

    private ToolCallback startLanding(
            String projectId,
            String actor,
            String runId,
            AdminAuthService.AuthPrincipal principal) {
        Function<StartLandingInput, String> function = input -> {
            permissionService.assertCanLandPackage(projectId, principal);
            return JSON.toJSONString(execute(
                    projectId,
                    actor,
                    runId,
                    "change_package_start_landing",
                    landingArguments(input)));
        };
        return FunctionToolCallback.builder(START_LANDING_TOOL, function)
                .description("Start the platform-owned Landing ReAct runtime for one already-approved ChangePackage. This tool only starts the governed Landing process; the Daily Agent itself never receives production-write authority.")
                .inputType(StartLandingInput.class)
                .build();
    }

    private Map<String, Object> execute(
            String projectId,
            String actor,
            String runId,
            String toolName,
            Map<String, Object> arguments) {
        return toolExecutionService.execute(Map.of(
                "projectId", projectId,
                "userId", actor,
                "runId", runId,
                "executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name(),
                "toolsetId", "change_package",
                "toolName", toolName,
                "arguments", arguments), actor);
    }

    private Map<String, Object> approveArguments(
            ApproveInput input,
            AdminAuthService.AuthPrincipal principal) {
        if (input == null) throw new IllegalArgumentException("CHANGE_PACKAGE_APPROVE_INPUT_REQUIRED");
        LinkedHashMap<String, Object> values = base(input.packageId(), input.version(), input.packageHash());
        values.put("actorScope", principal.scope());
        put(values, "comment", input.comment());
        return Map.copyOf(values);
    }

    private Map<String, Object> rejectArguments(RejectInput input) {
        if (input == null) throw new IllegalArgumentException("CHANGE_PACKAGE_REJECT_INPUT_REQUIRED");
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("packageId", required(input.packageId(), "CHANGE_PACKAGE_ID_REQUIRED"));
        put(values, "reason", input.reason());
        return Map.copyOf(values);
    }

    private Map<String, Object> landingArguments(StartLandingInput input) {
        if (input == null) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_INPUT_REQUIRED");
        LinkedHashMap<String, Object> values = base(input.packageId(), input.version(), input.packageHash());
        put(values, "idempotencyKey", input.idempotencyKey());
        return Map.copyOf(values);
    }

    private LinkedHashMap<String, Object> base(String packageId, int version, String packageHash) {
        if (version <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_VERSION_INVALID");
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("packageId", required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"));
        values.put("version", version);
        values.put("packageHash", required(packageHash, "CHANGE_PACKAGE_HASH_REQUIRED"));
        return values;
    }

    private void requireContext(String projectId, String actor, String runId) {
        required(projectId, "PROJECT_ID_REQUIRED");
        required(actor, "CHANGE_PACKAGE_ACTOR_REQUIRED");
        required(runId, "RUN_ID_REQUIRED");
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) values.put(key, value);
    }

    private String required(String value, String code) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }

    public record ApproveInput(String packageId, int version, String packageHash, String comment) {
    }

    public record RejectInput(String packageId, String reason) {
    }

    public record StartLandingInput(String packageId, int version, String packageHash, String idempotencyKey) {
    }
}
