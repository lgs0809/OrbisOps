package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageValidationOperationAssessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure policy for pre-approval operation shape, risk, and safe validation execution. */
public final class ChangePackagePreApprovalValidationPolicy {

    private static final Set<String> VALIDATION_EFFECTS = Set.of(
            "VALIDATE_ONLY",
            "DRY_RUN",
            "MUTATE_EPHEMERAL",
            "MUTATE_TEST_RESOURCE");
    private static final Set<String> TARGET_SCOPES = Set.of(
            "PRODUCTION",
            "TARGET_RESOURCE_WRITE");

    public ChangePackageValidationOperationAssessment assess(
            String packageRiskLevel,
            List<ChangePackageValidationOperation> operations) {
        List<ChangePackageValidationOperation> safeOperations =
                operations == null ? List.of() : List.copyOf(operations);
        List<String> errors = new ArrayList<>();
        String effectiveRisk = normalizedRisk(packageRiskLevel);
        boolean targetWrite = false;

        for (ChangePackageValidationOperation operation : safeOperations) {
            if (operation == null) continue;
            for (String field : operation.missingRequiredFields()) {
                errors.add("MISSING_OPERATION_FIELD:"
                        + field
                        + ":"
                        + operation.operationId());
            }
            if (operation.missingMcpBinding()) {
                errors.add("MISSING_OPERATION_FIELD:toolsetId/mcpId:"
                        + operation.operationId());
            }
            if (operation.policyUnknown()) {
                errors.add("POLICY_STALE_OR_UNKNOWN:" + operation.operationId());
            }
            if (operation.writesTargetResource() && !operation.requiresChangePackage()) {
                errors.add("TARGET_WRITE_REQUIRES_CHANGE_PACKAGE:"
                        + operation.operationId());
            }
            effectiveRisk = maxRisk(effectiveRisk, operation.riskLevel());
            targetWrite = targetWrite || operation.writesTargetResource();
        }

        return new ChangePackageValidationOperationAssessment(
                errors,
                effectiveRisk,
                highRisk(effectiveRisk) || targetWrite);
    }

    public boolean validationExecutable(ChangePackageValidationOperation operation) {
        if (operation == null) return false;
        return VALIDATION_EFFECTS.contains(operation.effectType())
                && !TARGET_SCOPES.contains(operation.effectScope());
    }

    private boolean highRisk(String risk) {
        String normalized = normalizedRisk(risk);
        return "HIGH".equals(normalized) || "CRITICAL".equals(normalized);
    }

    private String maxRisk(String left, String right) {
        String normalizedLeft = normalizedRisk(left);
        String normalizedRight = normalizedRisk(right);
        return score(normalizedRight) > score(normalizedLeft)
                ? normalizedRight
                : normalizedLeft;
    }

    private int score(String risk) {
        return switch (normalizedRisk(risk)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String normalizedRisk(String risk) {
        String normalized = risk == null ? "" : risk.trim().toUpperCase();
        return normalized.isBlank() ? "MEDIUM" : normalized;
    }
}
