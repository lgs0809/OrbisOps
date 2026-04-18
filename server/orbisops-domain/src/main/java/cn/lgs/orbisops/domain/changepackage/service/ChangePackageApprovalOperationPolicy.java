package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperation;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageApprovalOperationAssessment;

import java.util.List;

/** Pure approval policy for operation shape, canonical hashes, and target-write safety. */
public final class ChangePackageApprovalOperationPolicy {

    public ChangePackageApprovalOperationAssessment verify(
            String packageRiskLevel,
            List<ChangePackageApprovalOperation> operations) {
        List<ChangePackageApprovalOperation> safeOperations =
                operations == null ? List.of() : List.copyOf(operations);
        String effectiveRisk = normalizeRisk(packageRiskLevel);
        boolean targetWrite = false;
        for (ChangePackageApprovalOperation operation : safeOperations) {
            if (operation == null) continue;
            verifyOperation(operation);
            effectiveRisk = maxRisk(effectiveRisk, operation.riskLevel());
            targetWrite = targetWrite || operation.writesTargetResource();
        }
        return new ChangePackageApprovalOperationAssessment(
                effectiveRisk,
                targetWrite || highRisk(effectiveRisk));
    }

    private void verifyOperation(ChangePackageApprovalOperation operation) {
        String operationId = required(
                operation.present("operationId"), operation.operationId(), "operationId 不能为空");
        if (operation.mcpId().isBlank()) {
            throw new IllegalStateException("operation 缺少 toolsetId/mcpId");
        }
        required(operation.present("toolName"), operation.toolName(), "operation 缺少 toolName");
        required(operation.present("adapterType"), operation.adapterType(), "operation 缺少 adapterType");
        required(operation.present("arguments"), "arguments", "operation 缺少 arguments");
        required(operation.present("resourceScope"), operation.resourceScope(), "operation 缺少 resourceScope");
        required(operation.present("riskLevel"), operation.riskLevel(), "operation 缺少 riskLevel");
        required(operation.present("targetEnvironment"), operation.targetEnvironment(), "operation 缺少 targetEnvironment");
        required(operation.present("effectType"), operation.effectType(), "operation 缺少 effectType");
        required(operation.present("effectScope"), operation.effectScope(), "operation 缺少 effectScope");
        required(operation.present("mutability"), operation.mutability(), "operation 缺少 mutability");
        required(operation.present("readOnly"), "readOnly", "operation 缺少 readOnly");
        required(operation.present("writesTargetResource"), "writesTargetResource", "operation 缺少 writesTargetResource");
        required(operation.present("requiresChangePackage"), "requiresChangePackage", "operation 缺少 requiresChangePackage");
        required(operation.present("requiresApproval"), "requiresApproval", "operation 缺少 requiresApproval");
        if (operation.policyUnknown()) {
            throw new IllegalStateException(
                    "operation effect/effectScope/mutability UNKNOWN，拒绝审批：" + operationId);
        }
        verifyHashes(operation, operationId);
        if (!operation.writesTargetResource()) return;
        if (operation.raw().containsKey("additionalChecks")) {
            throw new IllegalStateException("生产写 operation 的附加检查未纳入 postCheck，必须重新准备：" + operationId);
        }
        if (!operation.requiresChangePackage() || !operation.requiresApproval()) {
            throw new IllegalStateException(
                    "生产写 operation 必须显式要求 ChangePackage 和审批：" + operationId);
        }
        requireStructure(operation.preconditionsPresent(),
                "生产写 operation 缺少 preconditions：" + operationId);
        requireStructure(operation.postCheckPresent(),
                "生产写 operation 缺少 postCheck：" + operationId);
        requireStructure(operation.rollbackPlanPresent(),
                "生产写 operation 缺少 rollbackPlan：" + operationId);
        requireStructure(operation.rollbackPreconditionPresent(),
                "生产写 operation 缺少 rollbackPrecondition：" + operationId);
        requireStructure(operation.manualFallbackPresent(),
                "生产写 operation 缺少 manualFallback：" + operationId);
    }

    private void verifyHashes(ChangePackageApprovalOperation operation, String operationId) {
        ChangePackageCanonicalHasher.OperationHashes actual =
                ChangePackageCanonicalHasher.calculateOperationHashes(operation.raw());
        requireHash(operation.argumentsHash(), actual.argumentsHash(), "argumentsHash", operationId);
        requireHash(operation.preconditionHash(), actual.preconditionHash(), "preconditionHash", operationId);
        requireHash(operation.postCheckHash(), actual.postCheckHash(), "postCheckHash", operationId);
        requireHash(operation.rollbackHash(), actual.rollbackHash(), "rollbackHash", operationId);
        requireHash(operation.operationHash(), actual.operationHash(), "operationHash", operationId);
    }

    private void requireHash(String expected, String actual, String key, String operationId) {
        if (expected == null || expected.isBlank()) {
            throw new IllegalStateException("operation 缺少 " + key + "：" + operationId);
        }
        if (!expected.equals(actual)) {
            throw new IllegalStateException("operation " + key + " 不匹配：" + operationId);
        }
    }

    private String required(boolean present, String value, String message) {
        if (!present || value == null || value.isBlank()) throw new IllegalStateException(message);
        return value;
    }

    private void requireStructure(boolean present, String message) {
        if (!present) throw new IllegalStateException(message);
    }

    private boolean highRisk(String risk) {
        String normalized = normalizeRisk(risk);
        return "HIGH".equals(normalized) || "CRITICAL".equals(normalized);
    }

    private String maxRisk(String left, String right) {
        String normalizedLeft = normalizeRisk(left);
        String normalizedRight = normalizeRisk(right);
        return score(normalizedRight) > score(normalizedLeft)
                ? normalizedRight
                : normalizedLeft;
    }

    private int score(String risk) {
        return switch (normalizeRisk(risk)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private String normalizeRisk(String risk) {
        String normalized = risk == null ? "" : risk.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.isBlank() ? "MEDIUM" : normalized;
    }
}
