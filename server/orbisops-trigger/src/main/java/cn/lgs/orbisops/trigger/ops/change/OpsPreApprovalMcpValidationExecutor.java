package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackagePreApprovalValidationPolicy;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Obtains or executes trusted MCP validation proof for a frozen operation set. */
final class OpsPreApprovalMcpValidationExecutor {

    private static final ChangePackagePreApprovalValidationPolicy VALIDATION_POLICY =
            new ChangePackagePreApprovalValidationPolicy();
    private static final List<String> TRUSTED_PROOF_TYPES = List.of(
            "MCP_DRY_RUN",
            "TOOL_VALIDATION",
            "CI_DRY_RUN");

    private final OpsToolExecutionService toolExecutionService;
    private final OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService;
    private final OpsPreApprovalProofService proofService;

    OpsPreApprovalMcpValidationExecutor(OpsToolExecutionService toolExecutionService,
                                       OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService,
                                       OpsPreApprovalProofService proofService) {
        this.toolExecutionService = toolExecutionService;
        this.projectMcpRuntimeConfigService = projectMcpRuntimeConfigService;
        this.proofService = proofService;
    }

    OpsPreApprovalValidationExecutionResult obtainProof(
            Map<String, Object> snapshot,
            List<OpsPreApprovalOperationMapper.MappedOperation> operations,
            String actor) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> proofs = new ArrayList<>();
        String existingProofType = proofService == null
                ? ""
                : proofService.firstTrustedType(snapshot, TRUSTED_PROOF_TYPES);
        if (hasText(existingProofType)) {
            proofs.add(proofService.sourceRef(existingProofType, "", Map.of()));
            return OpsPreApprovalValidationExecutionResult.completed(true, errors, proofs);
        }
        if (toolExecutionService == null) {
            errors.add("MCP_VALIDATION_EXECUTOR_UNAVAILABLE");
            errors.add("MISSING_TRUSTED_VALIDATION_PROOF");
            return OpsPreApprovalValidationExecutionResult.completed(false, errors, proofs);
        }
        if (projectMcpRuntimeConfigService == null) {
            errors.add("MCP_PROJECT_RUNTIME_CONFIG_UNAVAILABLE");
            errors.add("MISSING_TRUSTED_VALIDATION_PROOF");
            return OpsPreApprovalValidationExecutionResult.completed(false, errors, proofs);
        }
        boolean anyTrusted = execute(snapshot, operations, actor, errors, proofs);
        if (!anyTrusted) {
            errors.add("MISSING_TRUSTED_VALIDATION_PROOF");
        }
        return OpsPreApprovalValidationExecutionResult.completed(anyTrusted, errors, proofs);
    }

    private boolean execute(Map<String, Object> snapshot,
                            List<OpsPreApprovalOperationMapper.MappedOperation> operations,
                            String actor,
                            List<String> errors,
                            List<Map<String, Object>> proofs) {
        boolean anyTrusted = false;
        for (OpsPreApprovalOperationMapper.MappedOperation operation : safe(operations)) {
            if (!VALIDATION_POLICY.validationExecutable(operation.facts())) continue;

            String projectId = text(snapshot.get("projectId"), "");
            OpsMcpServerConfig config = projectMcpRuntimeConfigService
                    .resolve(projectId, operation.facts().mcpId())
                    .orElse(null);
            if (config == null) {
                errors.add("MCP_VALIDATION_TOOL_NOT_AVAILABLE:"
                        + operation.facts().operationId());
                continue;
            }
            config.setProjectId(projectId);
            config.setRunId(text(snapshot.get("runId"), ""));
            config.setAgentId("pre-approval-validation");
            config.setNodeId("validate-change-package");
            config.setToolCallStage("PREPARE");
            try {
                Map<String, Object> response = toolExecutionService.executeMcp(
                        config,
                        JSON.toJSONString(Map.of(
                                "toolName", operation.remoteToolName(),
                                "arguments", operation.arguments())),
                        actor);
                String outputHash = text(response.get("outputHash"), "");
                if (!hasText(outputHash)) {
                    errors.add("MCP_VALIDATION_MISSING_OUTPUT_HASH:"
                            + operation.facts().operationId());
                    continue;
                }
                String proofType = "DRY_RUN".equals(operation.facts().effectType())
                        ? "MCP_DRY_RUN"
                        : "TOOL_VALIDATION";
                Map<String, Object> proof = proofService.record(
                        snapshot,
                        text(snapshot.get("riskLevel"), operation.facts().riskLevel()),
                        proofType,
                        "TOOL_EXECUTED",
                        "",
                        Map.of(
                                "operationId", operation.facts().operationId(),
                                "toolResultId", text(response.get("resultId"), ""),
                                "outputHash", outputHash),
                        actor);
                proofs.add(proofService.sourceRef(proof));
                anyTrusted = true;
            } catch (RuntimeException e) {
                errors.add("MCP_VALIDATION_FAILED:"
                        + operation.facts().operationId()
                        + ":"
                        + text(e.getMessage(), ""));
            }
        }
        return anyTrusted;
    }

    private List<OpsPreApprovalOperationMapper.MappedOperation> safe(
            List<OpsPreApprovalOperationMapper.MappedOperation> operations) {
        return operations == null ? List.of() : operations;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return hasText(normalized) ? normalized : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
