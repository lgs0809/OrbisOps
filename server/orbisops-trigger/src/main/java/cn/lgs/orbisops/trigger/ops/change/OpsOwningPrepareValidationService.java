package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackagePreApprovalValidationPolicy;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs only non-production validation operations inside the owning PREPARE Agent run.
 * The deterministic ChangePackage compiler consumes the server-owned prepareExecution
 * projection but never starts a second tool chain itself.
 */
@Service
final class OpsOwningPrepareValidationService {

    private static final ChangePackagePreApprovalValidationPolicy VALIDATION_POLICY =
            new ChangePackagePreApprovalValidationPolicy();
    private static final OpsPreApprovalOperationMapper OPERATION_MAPPER =
            new OpsPreApprovalOperationMapper();

    private final OpsToolExecutionService toolExecutionService;
    private final OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService;

    OpsOwningPrepareValidationService(OpsToolExecutionService toolExecutionService,
                                      OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService) {
        this.toolExecutionService = toolExecutionService;
        this.projectMcpRuntimeConfigService = projectMcpRuntimeConfigService;
    }

    Map<String, Object> enrich(Map<String, Object> request, String actor) {
        Map<String, Object> enriched = new LinkedHashMap<>(request == null ? Map.of() : request);
        List<Map<String, Object>> steps = operationMaps(enriched.get("mcpSteps"));
        List<OpsPreApprovalOperationMapper.MappedOperation> operations = OPERATION_MAPPER.map(steps);
        OpsPreApprovalOperationMapper.MappedOperation validation = operations.stream()
                .filter(operation -> VALIDATION_POLICY.validationExecutable(operation.facts()))
                .findFirst()
                .orElse(null);
        if (validation == null) return enriched;

        String projectId = text(enriched.get("projectId"));
        String runId = text(enriched.get("runId"));
        Map<String, Object> prepareExecution = new LinkedHashMap<>();
        prepareExecution.put("status", "VALIDATION_PENDING");
        prepareExecution.put("source", "OWNING_PREPARE_AGENT_RUN");
        prepareExecution.put("verified", false);
        prepareExecution.put("preflightResult", Map.of(
                "status", "PASSED",
                "source", "OWNING_PREPARE_AGENT_RUN",
                "verified", true,
                "operationId", validation.facts().operationId()));

        OpsMcpServerConfig config = projectMcpRuntimeConfigService == null
                ? null
                : projectMcpRuntimeConfigService.resolve(projectId, validation.facts().mcpId()).orElse(null);
        if (toolExecutionService == null || config == null) {
            applyFailedProof(prepareExecution, validation,
                    config == null ? "MCP_VALIDATION_TOOL_NOT_AVAILABLE" : "MCP_VALIDATION_EXECUTOR_UNAVAILABLE");
            enriched.put("prepareExecution", Map.copyOf(prepareExecution));
            return enriched;
        }

        config.setProjectId(projectId);
        config.setRunId(runId);
        config.setAgentId("owning-prepare-agent");
        config.setNodeId("prepare-change-package");
        config.setToolCallStage("PREPARE");
        String idempotencyKey = "change-package:prepare-validation:" + CanonicalObjectHasher.sha256(Map.of(
                "projectId", projectId,
                "runId", runId,
                "operationId", validation.facts().operationId(),
                "mcpId", validation.facts().mcpId(),
                "toolName", validation.remoteToolName(),
                "arguments", validation.arguments()));
        try {
            toolExecutionService.enableMcpTool(
                    config,
                    JSON.toJSONString(Map.of(
                            "toolName", validation.remoteToolName(),
                            "reason", "owning PREPARE validation")),
                    actor);
            Map<String, Object> response = toolExecutionService.executeMcp(
                    config,
                    JSON.toJSONString(Map.of(
                            "toolName", validation.remoteToolName(),
                            "arguments", validation.arguments())),
                    actor,
                    idempotencyKey);
            String resultId = text(response.get("resultId"));
            String outputHash = text(response.get("outputHash")).toLowerCase();
            if (resultId.isBlank() || !outputHash.matches("[0-9a-f]{64}")) {
                applyFailedProof(prepareExecution, validation, "MCP_VALIDATION_RESULT_IDENTITY_MISSING");
            } else {
                Map<String, Object> proof = new LinkedHashMap<>();
                proof.put("status", "PASSED");
                proof.put("source", "OWNING_PREPARE_TOOL_EXECUTION");
                proof.put("verified", true);
                proof.put("executed", true);
                proof.put("operationId", validation.facts().operationId());
                proof.put("toolResultId", resultId);
                proof.put("outputHash", outputHash);
                proof.put("idempotencyKey", idempotencyKey);
                ServiceRestartSnapshot observedSnapshot = observedServiceRestartSnapshot(response, validation);
                if (observedSnapshot != null) {
                    proof.put("observedExpectedVersion", observedSnapshot.expectedVersion());
                    proof.put("observedRestartCount", observedSnapshot.restartCount());
                    proof.put("observedServiceStatus", observedSnapshot.serviceStatus());
                    enriched.put("mcpSteps", freezeObservedServiceRestartSafety(
                            steps,
                            validation,
                            observedSnapshot));
                }
                proof.put("validationEffectType", validation.facts().effectType());
                prepareExecution.put("dryRunResult", Map.copyOf(proof));
                prepareExecution.put("status", "SUCCEEDED");
                prepareExecution.put("verified", true);
            }
        } catch (RuntimeException error) {
            applyFailedProof(prepareExecution, validation,
                    "MCP_VALIDATION_FAILED:" + text(error.getMessage()));
        }
        enriched.put("prepareExecution", Map.copyOf(prepareExecution));
        return enriched;
    }

    private ServiceRestartSnapshot observedServiceRestartSnapshot(
            Map<String, Object> response,
            OpsPreApprovalOperationMapper.MappedOperation validation) {
        if (!"restart_service_dry_run".equals(validation.remoteToolName())) return null;
        if (response == null) return null;
        Object providerValue = response.get("providerResult");
        Map<?, ?> provider = providerValue instanceof Map<?, ?> nested ? nested : response;
        if (!"PASSED".equalsIgnoreCase(text(provider.get("status")))) return null;
        String observedService = text(provider.get("service"));
        String requestedService = text(objectMap(validation.arguments()).get("service"));
        if (requestedService.isBlank() || !requestedService.equals(observedService)) return null;
        Integer expectedVersion = nonNegativeInteger(provider.get("expectedVersion"));
        Integer restartCount = nonNegativeInteger(provider.get("restartCount"));
        String serviceStatus = text(provider.get("serviceStatus"));
        if (expectedVersion == null || restartCount == null || serviceStatus.isBlank()) return null;
        return new ServiceRestartSnapshot(expectedVersion, restartCount, serviceStatus);
    }

    private List<Map<String, Object>> freezeObservedServiceRestartSafety(
            List<Map<String, Object>> steps,
            OpsPreApprovalOperationMapper.MappedOperation validation,
            ServiceRestartSnapshot snapshot) {
        List<Map<String, Object>> result = new ArrayList<>();
        String validationService = text(objectMap(validation.arguments()).get("service"));
        for (Map<String, Object> source : steps == null ? List.<Map<String, Object>>of() : steps) {
            Map<String, Object> step = new LinkedHashMap<>(source);
            String remoteToolName = text(firstNonNull(step.get("remoteToolName"), step.get("toolName")));
            String mcpId = text(step.get("mcpId"));
            Map<String, Object> arguments = objectMap(step.get("arguments"));
            String service = text(arguments.get("service"));
            if ("restart_service".equals(remoteToolName)
                    && validation.facts().mcpId().equals(mcpId)
                    && validationService.equals(service)) {
                Map<String, Object> frozen = new LinkedHashMap<>(arguments);
                frozen.put("expectedVersion", snapshot.expectedVersion());
                step.put("arguments", Map.copyOf(frozen));
                step.putIfAbsent("postCheck", Map.of(
                        "type", "SERVICE_STATE",
                        "sourceTool", "get_service_status",
                        "expectedValues", Map.of(
                                "serviceStatus", snapshot.serviceStatus(),
                                "version", snapshot.expectedVersion() + 1,
                                "restartCount", snapshot.restartCount() + 1)));
                step.putIfAbsent("rollbackPlan", Map.of(
                        "type", "MANUAL_RECOVERY",
                        "required", true,
                        "status", "MANUAL_REQUIRED",
                        "automaticMutation", false,
                        "summary", "Restore the previous healthy service deployment or instance if verification fails."));
                step.putIfAbsent("rollbackPrecondition", Map.of(
                        "type", "VERIFICATION_FAILURE",
                        "requiresHumanApproval", true,
                        "automaticRollback", false));
                step.putIfAbsent("manualFallback", Map.of(
                        "required", true,
                        "action", "ESCALATE_TO_ONCALL_AND_RESTORE_PREVIOUS_DEPLOYMENT",
                        "automaticMutation", false));
            }
            result.add(Map.copyOf(step));
        }
        return List.copyOf(result);
    }

    private Integer nonNegativeInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue() >= 0 ? number.intValue() : null;
        }
        try {
            int parsed = Integer.parseInt(text(value));
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ServiceRestartSnapshot(int expectedVersion,
                                          int restartCount,
                                          String serviceStatus) {
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private void applyFailedProof(Map<String, Object> prepareExecution,
                                  OpsPreApprovalOperationMapper.MappedOperation validation,
                                  String reason) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("status", "FAILED");
        proof.put("source", "OWNING_PREPARE_TOOL_EXECUTION");
        proof.put("verified", false);
        proof.put("executed", false);
        proof.put("operationId", validation.facts().operationId());
        proof.put("reason", text(reason));
        proof.put("validationEffectType", validation.facts().effectType());
        prepareExecution.put("dryRunResult", Map.copyOf(proof));
        prepareExecution.put("status", "FAILED");
        prepareExecution.put("verified", false);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> operationMaps(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> operation = new LinkedHashMap<>();
                map.forEach((key, entry) -> operation.put(String.valueOf(key), entry));
                result.add(operation);
            }
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
