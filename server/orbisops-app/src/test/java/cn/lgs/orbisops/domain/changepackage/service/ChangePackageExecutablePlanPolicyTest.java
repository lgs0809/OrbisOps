package cn.lgs.orbisops.domain.changepackage.service;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ChangePackageExecutablePlanPolicyTest {
    private final ChangePackageExecutablePlanPolicy policy = new ChangePackageExecutablePlanPolicy();
    private final Map<String, Object> forward = action("op-a", "v5", "v6");
    private final Map<String, Object> recovery = action("op-b", "v6", "v5");

    @Test void refusesMetadataOnlyPlanRatherThanExecutingAllActions() {
        var error = assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                "forwardOperationId", "op-a", "recoveryOperationId", "op-b"))));
        assertTrue(error.getMessage().contains("EXPLICIT_FORWARD_STEPS_REQUIRED"));
    }
    @Test void legalForwardReferenceKeepsRecoverySeparateAndUsesCompleteGovernedAction() {
        var plan = Map.of("steps", List.of(Map.of("operationId", "op-a")),
                "forwardOperationId", "op-a", "recoveryOperationId", "op-b");
        var selected = policy.resolve(snapshot(plan));
        assertEquals(List.of(forward), selected);
        assertEquals(recovery, ((List<?>) snapshot(plan).get("mcpSteps")).get(1));
    }
    @Test void freezesShortReferencesAsCompleteActionsBeforeHashesAreSealed() {
        var plan = policy.canonicalPreferredPlan(Map.of("steps", List.of(Map.of("operationId", "op-a"))),
                List.of(forward, recovery));
        assertEquals(List.of(forward), plan.get("steps"));
        assertEquals(List.of(forward), policy.resolve(snapshot(plan)));
    }
    @Test void neverAcceptsRecoveryAsASecondForwardWrite() {
        var error = assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                "steps", List.of(forward, recovery), "recoveryOperationId", "op-b"))));
        assertTrue(error.getMessage().contains("RECOVERY_OPERATION_MUST_BE_SEPARATE"));
    }
    @Test void multipleForwardActionsAreNotGuessedToBeRecoveryByTheirNames() {
        assertEquals(List.of(forward, recovery), policy.resolve(snapshot(Map.of("steps", List.of(forward, recovery)))));
    }
    @Test void rejectsUnknownOrDuplicateReferencesAndAmbiguousAliases() {
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                "steps", List.of(Map.of("operationId", "missing"))))));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                "steps", List.of(forward, forward)))));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                "steps", List.of(forward), "operations", List.of(recovery)))));
    }
    @Test void preventsArgumentOrPostcheckChangesThroughThePreferredView() {
        for (var field : List.of("arguments", "postCheck", "rollbackPrecondition")) {
            var edited = new LinkedHashMap<>(forward); edited.put(field, Map.of("version", "unapproved"));
            var error = assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                    "steps", List.of(edited)))));
            assertTrue(error.getMessage().contains("FORWARD_OPERATION_CONTRACT_MISMATCH:" + field));
        }
    }
    @Test void rejectsEveryConflictingExecutionOrPermissionOverride() {
        for (var field : List.of("readOnly", "writesTargetResource", "purpose", "effectType", "requiresApproval", "unknownControl")) {
            var edited = new LinkedHashMap<>(forward); edited.put(field, "unapproved");
            assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of("steps", List.of(edited)))));
        }
        var displayed = new LinkedHashMap<>(forward); displayed.put("summary", "human explanation");
        assertEquals(List.of(forward), policy.resolve(snapshot(Map.of("steps", List.of(displayed)))));
    }
    @Test void unknownPlanControlsAreRejectedInsteadOfBecomingNewExecutionAliases() {
        for (var field : List.of("rollbackOperationId", "normalSequence", "executionMode")) {
            var error = assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of(
                    "steps", List.of(forward), field, "op-b"))));
            assertTrue(error.getMessage().contains("FORWARD_PLAN_CONTROL_UNSUPPORTED:" + field));
        }
    }
    @Test void retainsStepAliasesLegacyJsonAndEmptyPlanFallback() {
        for (var key : List.of("steps", "operations", "mcpSteps")) {
            assertEquals(List.of(forward), policy.resolve(snapshot(Map.of(key, List.of(forward)))));
        }
        assertEquals(List.of(forward, recovery), policy.resolve(snapshot(Map.of())));
        assertEquals(List.of(forward), policy.resolve(Map.of("mcpStepsJson", "[" + cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(forward) + "]",
                "landingPlanJson", "{\"preferredPlan\":{\"steps\":[{\"operationId\":\"op-a\"}]}}")));
    }
    @Test void retainsApprovedInlinePlanAndManualNoActionViews() {
        assertEquals(List.of(forward), policy.resolve(Map.of("landingPlan", Map.of("preferredPlan", Map.of("planId", "legacy-inline", "steps", List.of(forward))))));
        assertTrue(policy.resolve(Map.of("landingPlan", Map.of("preferredPlan", Map.of("summary", "manual only")))).isEmpty());
    }
    @Test void malformedOrExplicitlyEmptyStepsCannotSilentlyFallback() {
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of("steps", "explain only"))));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of("steps", List.of()))));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(snapshot(Map.of("steps", List.of("op-a")))));
    }
    @Test void historicalMetadataCanOnlyReobserveItsExactCompletedJournalOperation() {
        var action = hashed(forward);
        var historical = new LinkedHashMap<>(recordedSnapshot(action));
        historical.put("landingPlan", Map.of("preferredPlan", Map.of("planId", "old-plan", "summary", "old metadata")));
        assertThrows(IllegalArgumentException.class, () -> policy.resolve(historical));
        var recorded = recorded(action);
        assertEquals(List.of(action), policy.recordedOperations(historical, "lr-1", List.of(recorded)));
        // Recovery is in the approved catalog but was never executed: it is not selected for observation.
        historical.put("mcpSteps", List.of(action, hashed(recovery)));
        assertEquals(List.of(action), policy.recordedOperations(historical, "lr-1", List.of(recorded)));
    }
    @Test void recordedViewRejectsMissingUnexecutedUnknownOrForeignProof() {
        var action = hashed(forward); var snapshot = recordedSnapshot(action);
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of()));
        for (var field : List.of("landing_run_id", "package_id", "project_id", "approved_version", "approved_package_hash",
                "operation_id", "operation_hash", "execution_key", "fact_status", "status", "result_id", "output_hash")) {
            var changed = new LinkedHashMap<>(recorded(action)); changed.put(field, "");
            assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of(changed)), field);
        }
        var pending = new LinkedHashMap<>(recorded(action)); pending.put("fact_status", "NONE"); pending.put("status", "PENDING");
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of(pending)));
        var unknown = new LinkedHashMap<>(recorded(action)); unknown.put("fact_status", "UNKNOWN");
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of(unknown)));
    }
    @Test void recordedViewRejectsChangedSnapshotHashAndDuplicateReceipt() {
        var action = hashed(forward); var snapshot = recordedSnapshot(action); var recorded = recorded(action);
        var changed = new LinkedHashMap<>(recorded); changed.put("operation_snapshot_json", recovery);
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of(changed)));
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(snapshot, "lr-1", List.of(recorded, recorded)));
        var altered = new LinkedHashMap<>(action); altered.put("postCheck", Map.of("expectedValues", Map.of("version", "fake")));
        assertThrows(IllegalArgumentException.class, () -> policy.recordedOperations(recordedSnapshot(altered), "lr-1", List.of(recorded)));
    }
    private Map<String, Object> hashed(Map<String, Object> action) {
        var value = new LinkedHashMap<>(action); ChangePackageCanonicalHasher.applyOperationHashes(value); return value;
    }
    private Map<String, Object> recordedSnapshot(Map<String, Object> action) {
        return Map.of("projectId", "p", "packageId", "cp", "version", 1, "packageHash", "approved-hash", "mcpSteps", List.of(action));
    }
    private Map<String, Object> recorded(Map<String, Object> action) {
        return Map.ofEntries(Map.entry("landing_run_id", "lr-1"), Map.entry("project_id", "p"), Map.entry("package_id", "cp"),
                Map.entry("approved_version", 1), Map.entry("approved_package_hash", "approved-hash"),
                Map.entry("operation_id", action.get("operationId")), Map.entry("operation_hash", action.get("operationHash")),
                Map.entry("operation_snapshot_json", cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(action)),
                Map.entry("execution_key", ChangePackageCanonicalHasher.landingExecutionKey("p", "cp", "1", "approved-hash",
                        String.valueOf(action.get("operationId")), String.valueOf(action.get("operationHash")))),
                Map.entry("fact_status", "COMPLETED"), Map.entry("status", "SUCCEEDED"), Map.entry("result_id", "receipt-1"),
                Map.entry("output_hash", "a".repeat(64)));
    }
    private Map<String, Object> snapshot(Map<String, ?> preferred) {
        return Map.of("mcpSteps", List.of(forward, recovery), "landingPlan", Map.of("preferredPlan", preferred));
    }
    private static Map<String, Object> action(String id, String from, String to) {
        return Map.of("operationId", id, "toolName", "apply", "mcpId", "reviewed-mcp", "writesTargetResource", true,
                "readOnly", false, "arguments", Map.of("expectedVersion", from, "version", to),
                "postCheck", Map.of("expectedValues", Map.of("version", to)),
                "rollbackPrecondition", Map.of("expectedVersion", to));
    }
}
