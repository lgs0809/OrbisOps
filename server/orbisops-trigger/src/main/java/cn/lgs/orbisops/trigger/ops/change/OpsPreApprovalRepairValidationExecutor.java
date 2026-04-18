package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes the bounded Repair/Controlled-Bash pre-approval validation flow. */
final class OpsPreApprovalRepairValidationExecutor {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();

    private final OpsRepairWorkspaceService repairWorkspaceService;
    private final OpsToolExecutionService toolExecutionService;
    private final OpsPreApprovalProofService proofService;

    OpsPreApprovalRepairValidationExecutor(OpsRepairWorkspaceService repairWorkspaceService,
                                           OpsToolExecutionService toolExecutionService,
                                           OpsPreApprovalProofService proofService) {
        this.repairWorkspaceService = repairWorkspaceService;
        this.toolExecutionService = toolExecutionService;
        this.proofService = proofService;
    }

    OpsPreApprovalValidationExecutionResult validate(Map<String, Object> snapshot,
                                                     String actor) {
        if (repairWorkspaceService == null || proofService == null || !proofService.available()) {
            return OpsPreApprovalValidationExecutionResult.failed("REPAIR_VALIDATION_SERVICE_UNAVAILABLE");
        }
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> proofs = new ArrayList<>();
        String workspaceId = require(snapshot, "repairWorkspaceId", errors);
        String diffHash = require(snapshot, "diffHash", errors);
        String testProofHash = text(snapshot.get("testProofHash"), "");
        require(snapshot, "repairCommit", errors);
        if (!errors.isEmpty()) {
            return OpsPreApprovalValidationExecutionResult.completed(false, errors, proofs);
        }

        boolean hasTestProof = proofService.trusted("CONTROLLED_BASH_TEST", snapshot, testProofHash)
                || proofService.trusted("CI_DRY_RUN", snapshot, testProofHash);
        if (hasTestProof) {
            String proofType = proofService.trusted("CONTROLLED_BASH_TEST", snapshot, testProofHash)
                    ? "CONTROLLED_BASH_TEST"
                    : "CI_DRY_RUN";
            proofs.add(proofService.sourceRef(
                    proofType,
                    testProofHash,
                    Map.of("testProofHash", testProofHash)));
        } else {
            testProofHash = executeControlledTest(
                    snapshot, workspaceId, actor, errors, proofs);
            hasTestProof = hasText(testProofHash)
                    && (proofService.trusted("CONTROLLED_BASH_TEST", snapshot, testProofHash)
                    || proofService.trusted("CI_DRY_RUN", snapshot, testProofHash));
            if (!hasTestProof) {
                errors.add("MISSING_TRUSTED_TEST_PROOF");
                return OpsPreApprovalValidationExecutionResult.completed(false, errors, proofs);
            }
        }

        List<String> changedFiles = STRUCTURED_VALUE_READER.stringList(firstNonNull(
                snapshot.get("changedFiles"),
                snapshot.get("changedFilesJson")));
        Map<String, Object> verified = repairWorkspaceService.verifyRepairWorkspace(
                workspaceId,
                diffHash,
                changedFiles,
                testProofHash,
                text(snapshot.get("createBy"), "ops-agent"));
        Map<String, Object> workspaceProof = proofService.record(
                snapshot,
                text(snapshot.get("riskLevel"), "MEDIUM"),
                "REPAIR_WORKSPACE_VERIFIED",
                "REPAIR_WORKSPACE_VERIFIED",
                workspaceId,
                verified,
                text(snapshot.get("createBy"), "ops-agent"));
        proofs.add(proofService.sourceRef(workspaceProof));
        return OpsPreApprovalValidationExecutionResult.completed(true, errors, proofs);
    }

    private String executeControlledTest(Map<String, Object> snapshot,
                                         String workspaceId,
                                         String actor,
                                         List<String> errors,
                                         List<Map<String, Object>> proofs) {
        if (toolExecutionService == null) {
            errors.add("CONTROLLED_BASH_EXECUTOR_UNAVAILABLE");
            return "";
        }
        String command = text(firstNonNull(
                snapshot.get("testCommand"),
                snapshot.get("validationCommand"),
                STRUCTURED_VALUE_READER.firstString(snapshot.get("testCommands")),
                STRUCTURED_VALUE_READER.firstString(snapshot.get("validationCommands"))), "");
        if (!hasText(command)) {
            errors.add("MISSING_CONTROLLED_TEST_COMMAND");
            return "";
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("projectId", text(snapshot.get("projectId"), ""));
        arguments.put("workspaceId", workspaceId);
        arguments.put("command", command);
        arguments.put("expectedEffect", "TEST_OR_BUILD");
        arguments.put("reason", "pre-approval validation");
        arguments.put("packageId", text(snapshot.get("packageId"), ""));
        arguments.put("packageVersion", intValue(snapshot.get("version"), 0));
        arguments.put("packageHash", text(snapshot.get("packageHash"), ""));
        Map<String, Object> response = toolExecutionService.execute(Map.of(
                "projectId", text(snapshot.get("projectId"), ""),
                "sessionId", text(snapshot.get("sessionId"), ""),
                "runId", text(snapshot.get("runId"), ""),
                "userId", text(actor, ""),
                "executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name(),
                "toolsetId", "code.repair",
                "toolName", "code_bash",
                "arguments", arguments), actor);
        String outputHash = text(response.get("outputHash"), "");
        if (!hasText(outputHash)) {
            errors.add("CONTROLLED_TEST_MISSING_OUTPUT_HASH");
            return "";
        }
        Map<String, Object> proof = proofService.record(
                snapshot,
                text(snapshot.get("riskLevel"), "MEDIUM"),
                "CONTROLLED_BASH_TEST",
                "CONTROLLED_BASH_EXECUTED",
                outputHash,
                Map.of(
                        "workspaceId", workspaceId,
                        "toolResultId", text(response.get("resultId"), ""),
                        "outputHash", outputHash,
                        "command", command),
                actor);
        proofs.add(proofService.sourceRef(proof));
        return outputHash;
    }

    private String require(Map<String, Object> snapshot,
                           String field,
                           List<String> errors) {
        String value = text(snapshot.get(field), "");
        if (!hasText(value)) errors.add("MISSING_" + field);
        return value;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value, ""));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return hasText(normalized) ? normalized : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
