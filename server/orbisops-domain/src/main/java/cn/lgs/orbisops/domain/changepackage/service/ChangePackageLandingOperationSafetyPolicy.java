package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperationSafetyDecision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure Domain policy for approved operation integrity and production-write safety requirements. */
public final class ChangePackageLandingOperationSafetyPolicy {

    private static final List<String> TARGET_WRITE_EFFECT_TYPES = List.of(
            "MUTATE_TARGET_RESOURCE",
            "EXECUTE_EXTERNAL_ACTION",
            "DELETE_TARGET_RESOURCE");
    private static final List<String> TARGET_WRITE_EFFECT_SCOPES = List.of(
            "PRODUCTION",
            "TARGET_RESOURCE_WRITE");

    public ChangePackageLandingOperationSafetyDecision verifyApprovedHashes(
            Map<String, Object> operation) {
        Map<String, Object> approved = operation == null ? Map.of() : operation;
        String operationId = text(firstNonNull(
                approved.get("operationId"), approved.get("operation_id")), "");
        String approvedArgumentsHash = text(firstNonNull(
                approved.get("argumentsHash"), approved.get("arguments_hash")), "");
        String approvedPreconditionHash = text(firstNonNull(
                approved.get("preconditionHash"), approved.get("precondition_hash")), "");
        String approvedPostCheckHash = text(firstNonNull(
                approved.get("postCheckHash"), approved.get("post_check_hash")), "");
        String approvedRollbackHash = text(firstNonNull(
                approved.get("rollbackHash"), approved.get("rollback_hash")), "");
        String approvedOperationHash = text(firstNonNull(
                approved.get("operationHash"), approved.get("operation_hash")), "");

        if (approvedOperationHash.isBlank()
                || approvedArgumentsHash.isBlank()
                || approvedPreconditionHash.isBlank()
                || approvedPostCheckHash.isBlank()
                || approvedRollbackHash.isBlank()) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "OPERATION_HASH_MISMATCH",
                    "审批快照 operation 缺少已审批 hash，operationId=" + operationId
                            + "，必须重新规划并生成新版本。",
                    "OPERATION_HASH_MISMATCH");
        }

        ChangePackageCanonicalHasher.OperationHashes hashes =
                ChangePackageCanonicalHasher.calculateOperationHashes(approved);
        if (!approvedArgumentsHash.equals(hashes.argumentsHash())
                || !approvedPreconditionHash.equals(hashes.preconditionHash())
                || !approvedPostCheckHash.equals(hashes.postCheckHash())
                || !approvedRollbackHash.equals(hashes.rollbackHash())) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "OPERATION_HASH_MISMATCH",
                    "审批快照 operation 分段 hash 与内容不一致，operationId=" + operationId
                            + "，禁止落地。",
                    "OPERATION_HASH_MISMATCH");
        }
        if (!approvedOperationHash.equals(hashes.operationHash())) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "OPERATION_HASH_MISMATCH",
                    "审批快照 operationHash 与 operation 内容不一致，operationId=" + operationId
                            + "，禁止落地。",
                    "OPERATION_HASH_MISMATCH");
        }
        return ChangePackageLandingOperationSafetyDecision.allowed();
    }

    public ChangePackageLandingOperationSafetyDecision verifyTargetWriteSafety(
            Map<String, Object> operation) {
        Map<String, Object> approved = operation == null ? Map.of() : operation;
        if (!targetWrite(approved)) {
            return ChangePackageLandingOperationSafetyDecision.allowed();
        }

        Map<String, Object> postCheck = objectValue(firstNonNull(
                approved.get("postCheck"), approved.get("postCheckPlan")));
        Map<String, Object> rollbackPlan = objectValue(firstNonNull(
                approved.get("rollbackPlan"), approved.get("rollbackSteps")));
        Map<String, Object> rollbackPrecondition = objectValue(firstNonNull(
                approved.get("rollbackPrecondition"),
                rollbackPlan.get("rollbackPrecondition")));
        Map<String, Object> manualFallback = objectValue(firstNonNull(
                approved.get("manualFallback"),
                rollbackPlan.get("manualFallback")));

        if (approved.containsKey("additionalChecks") || postCheck.isEmpty()
                || expectedValues(postCheck).isEmpty() || !additionalChecksValid(postCheck)) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "POST_CHECK_REQUIRED",
                    "生产写 operation 缺少审批通过的 postCheck expectedValues，禁止落地。",
                    "POST_CHECK_REQUIRED");
        }
        if (rollbackPlan.isEmpty()) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "ROLLBACK_PLAN_REQUIRED",
                    "生产写 operation 缺少审批通过的 rollbackPlan，禁止落地。",
                    "ROLLBACK_PLAN_REQUIRED");
        }
        if (rollbackPrecondition.isEmpty()) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "ROLLBACK_PRECONDITION_REQUIRED",
                    "生产写 operation 缺少 rollbackPrecondition，禁止落地。",
                    "ROLLBACK_PRECONDITION_REQUIRED");
        }
        if (manualFallback.isEmpty()) {
            return ChangePackageLandingOperationSafetyDecision.rejected(
                    "MANUAL_FALLBACK_REQUIRED",
                    "生产写 operation 缺少 manualFallback，禁止落地。",
                    "MANUAL_FALLBACK_REQUIRED");
        }
        return ChangePackageLandingOperationSafetyDecision.allowed();
    }

    public boolean targetWrite(Map<String, Object> operation) {
        if (operation == null || operation.isEmpty()) return false;
        return bool(operation.get("writesTargetResource"), false)
                || TARGET_WRITE_EFFECT_TYPES.contains(normalizeEffectType(operation.get("effectType")))
                || TARGET_WRITE_EFFECT_SCOPES.contains(
                        text(operation.get("effectScope"), "").toUpperCase(Locale.ROOT));
    }

    private String normalizeEffectType(Object value) {
        String effectType = text(value, "").toUpperCase(Locale.ROOT);
        return "MUTATE_TEMP_RESOURCE".equals(effectType) ? "MUTATE_EPHEMERAL" : effectType;
    }

    private boolean additionalChecksValid(Map<String, Object> contract) {
        if (!contract.containsKey("additionalChecks")) return true;
        if (!(contract.get("additionalChecks") instanceof java.util.List<?> checks) || checks.size() > 15) return false;
        return checks.stream().allMatch(item -> item instanceof Map<?, ?>
                && !objectValue(item).containsKey("additionalChecks")
                && !expectedValues(objectValue(item)).isEmpty());
    }

    private Map<String, Object> expectedValues(Map<String, Object> source) {
        Map<String, Object> values = objectValue(firstNonNull(
                source.get("expectedValues"),
                source.get("values"),
                source.get("currentState")));
        if (!values.isEmpty()) return values;
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (List.of("toolsetId", "toolName", "arguments", "summary", "description", "readSource")
                    .contains(key)) {
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private Map<String, Object> objectValue(Object value) {
        Object decoded = ChangePackageLegacyStructuredValue.decode(value);
        if (decoded instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
