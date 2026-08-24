package cn.lgs.orbisops.domain.changepackage.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves explicit forward plans against their governed operations, without guessing intent from names. */
public final class ChangePackageExecutablePlanPolicy {
    private static final List<String> STEP_KEYS = List.of("steps", "operations", "mcpSteps");
    private static final List<String> DISPLAY_FIELDS = List.of("summary", "description", "title");
    private static final List<String> PLAN_FIELDS = List.of("steps", "operations", "mcpSteps", "forwardOperationId",
            "recoveryOperationId", "summary", "description", "title", "planId", "sourcePackageId", "selectedCandidateId",
            "recommendedOrder", "expectedBeforeState", "expectedAfterState");

    public List<Map<String, Object>> resolve(Map<String, Object> snapshot) {
        var plan = map(first(snapshot.get("landingPlan"), snapshot.get("landingPlanJson")));
        var preferred = map(plan.get("preferredPlan"));
        var actions = list(first(snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson")));
        var declared = steps(preferred);
        if (!preferred.isEmpty() && !actions.isEmpty() && (declared == null || declared.isEmpty())) {
            fail("EXPLICIT_FORWARD_STEPS_REQUIRED");
        }
        var selected = declared != null ? declared : steps(plan);
        if (selected == null || preferred.isEmpty() && selected.isEmpty()) selected = actions;
        if (!actions.isEmpty() || !selected.isEmpty()) validatePlanControls(preferred);
        var registry = index(actions.isEmpty() ? selected : actions);
        var resolved = new ArrayList<Map<String, Object>>();
        var selectedIds = new java.util.HashSet<String>();
        for (var step : selected) {
            String id = id(step);
            var action = registry.get(id);
            if (id.isBlank() || action == null || !selectedIds.add(id)) fail("FORWARD_OPERATION_REFERENCE_INVALID");
            for (var field : step.keySet()) {
                if (DISPLAY_FIELDS.contains(field)) continue;
                String expected = action.containsKey(field) ? field : canonicalField(field);
                if (!action.containsKey(expected) || !same(step.get(field), action.get(expected))) {
                    fail("FORWARD_OPERATION_CONTRACT_MISMATCH:" + field);
                }
            }
            resolved.add(action);
        }
        for (var owner : List.of(plan, preferred)) {
            String forward = text(owner.get("forwardOperationId"));
            String recovery = text(owner.get("recoveryOperationId"));
            if (!forward.isBlank() && !selectedIds.contains(forward)) fail("FORWARD_OPERATION_REFERENCE_INVALID");
            if (!recovery.isBlank() && (!registry.containsKey(recovery) || selectedIds.contains(recovery))) {
                fail("RECOVERY_OPERATION_MUST_BE_SEPARATE");
            }
        }
        return List.copyOf(resolved);
    }

    public Map<String, Object> canonicalPreferredPlan(Map<String, Object> preferred,
                                                       List<Map<String, Object>> governedActions) {
        var selected = resolve(Map.of("mcpSteps", governedActions,
                "landingPlan", Map.of("preferredPlan", preferred)));
        var canonical = new LinkedHashMap<>(preferred);
        for (var key : STEP_KEYS) {
            if (canonical.containsKey(key)) canonical.put(key, selected);
        }
        return canonical;
    }

    /** Re-observation reads the immutable executed journal, never reinterprets a historical model plan. */
    public List<Map<String, Object>> recordedOperations(Map<String, Object> snapshot, String landingRunId,
                                                        List<Map<String, Object>> facts) {
        var actions = list(first(snapshot.get("mcpSteps"), snapshot.get("mcpStepsJson")));
        if (actions.isEmpty()) {
            var plan = map(first(snapshot.get("landingPlan"), snapshot.get("landingPlanJson")));
            var inline = steps(map(plan.get("preferredPlan")));
            actions = inline == null ? list(first(plan.get("steps"), first(plan.get("operations"), plan.get("mcpSteps")))) : inline;
        }
        var registry = index(actions);
        var selected = new ArrayList<Map<String, Object>>();
        var seen = new java.util.HashSet<String>();
        if (landingRunId == null || landingRunId.isBlank() || facts == null || facts.isEmpty()) fail("RECORDED_EXECUTION_REQUIRED");
        for (var fact : facts) {
            String operationId = text(fact.get("operation_id"));
            var action = registry.get(operationId);
            String hash = action == null ? "" : text(first(action.get("operationHash"), action.get("operation_hash")));
            if (!landingRunId.equals(text(fact.get("landing_run_id")))
                    || !same(snapshot.get("packageId"), fact.get("package_id"))
                    || !same(snapshot.get("projectId"), fact.get("project_id"))
                    || !same(snapshot.get("version"), fact.get("approved_version"))
                    || !same(snapshot.get("packageHash"), fact.get("approved_package_hash"))
                    || !"COMPLETED".equals(fact.get("fact_status")) || !"SUCCEEDED".equals(fact.get("status"))
                    || text(fact.get("result_id")).isBlank() || !text(fact.get("output_hash")).matches("[a-f0-9]{64}")
                    || action == null || !seen.add(operationId) || !hash.equals(text(fact.get("operation_hash")))
                    || !hash.equals(ChangePackageCanonicalHasher.calculateOperationHashes(action).operationHash())
                    || !same(action, map(fact.get("operation_snapshot_json")))
                    || !ChangePackageCanonicalHasher.landingExecutionKey(text(snapshot.get("projectId")),
                            text(snapshot.get("packageId")), text(snapshot.get("version")), text(snapshot.get("packageHash")),
                            operationId, hash).equals(text(fact.get("execution_key")))) {
                fail("RECORDED_EXECUTION_IDENTITY_MISMATCH");
            }
            selected.add(action);
        }
        return List.copyOf(selected);
    }

    private List<Map<String, Object>> steps(Map<String, Object> source) {
        List<Map<String, Object>> selected = null;
        for (var key : STEP_KEYS) {
            if (!source.containsKey(key)) continue;
            var candidate = list(source.get(key));
            if (selected != null && !same(selected, candidate)) fail("FORWARD_STEP_DECLARATIONS_CONFLICT");
            selected = candidate;
        }
        return selected;
    }

    private Map<String, Map<String, Object>> index(List<Map<String, Object>> actions) {
        var indexed = new LinkedHashMap<String, Map<String, Object>>();
        for (var action : actions) {
            String id = id(action);
            if (id.isBlank() || indexed.putIfAbsent(id, action) != null) fail("OPERATION_ID_MISSING_OR_DUPLICATED");
        }
        return indexed;
    }

    private List<Map<String, Object>> list(Object source) {
        var value = ChangePackageLegacyStructuredValue.decode(source);
        if (value == null) return List.of();
        if (!(value instanceof Iterable<?> items)) fail("FORWARD_STEPS_LIST_REQUIRED");
        var result = new ArrayList<Map<String, Object>>();
        for (var item : (Iterable<?>) value) {
            if (!(ChangePackageLegacyStructuredValue.decode(item) instanceof Map<?, ?>)) {
                fail("FORWARD_STEP_OBJECT_REQUIRED");
            }
            result.add(map(item));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> map(Object source) {
        var value = ChangePackageLegacyStructuredValue.decode(source);
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> input)) fail("FORWARD_PLAN_OBJECT_REQUIRED");
        var result = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private boolean same(Object left, Object right) {
        return ChangePackageCanonicalHasher.canonicalHash(ChangePackageLegacyStructuredValue.decode(left))
                .equals(ChangePackageCanonicalHasher.canonicalHash(ChangePackageLegacyStructuredValue.decode(right)));
    }
    private void validatePlanControls(Map<String, Object> preferred) {
        for (var field : preferred.keySet()) {
            if (!PLAN_FIELDS.contains(field)) fail("FORWARD_PLAN_CONTROL_UNSUPPORTED:" + field);
        }
        if (preferred.containsKey("recommendedOrder") && !"as_planned".equals(preferred.get("recommendedOrder"))) {
            fail("FORWARD_PLAN_ORDER_UNSUPPORTED");
        }
    }
    private String canonicalField(String field) {
        return switch (field) {
            case "args" -> "arguments"; case "precondition" -> "preconditions";
            case "postCheckPlan" -> "postCheck"; case "rollbackSteps" -> "rollbackPlan";
            case "operation_id" -> "operationId"; case "operation_hash" -> "operationHash";
            case "toolset_id" -> "toolsetId"; case "tool_name" -> "toolName";
            case "resource_key" -> "resourceKey"; case "effect_type" -> "effectType";
            default -> field;
        };
    }
    private String id(Map<String, Object> action) { return text(first(action.get("operationId"), action.get("operation_id"))); }
    private Object first(Object left, Object right) { return left == null ? right : left; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private void fail(String reason) {
        throw new IllegalArgumentException("CHANGE_PACKAGE_" + reason + ": 明确正常落地步骤；恢复操作只能单独受控执行，不能作为正常发布步骤");
    }
}
