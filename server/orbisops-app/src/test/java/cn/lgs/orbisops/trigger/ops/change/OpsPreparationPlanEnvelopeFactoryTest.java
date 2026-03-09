package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPreparationPlanEnvelopeFactoryTest {

    private final OpsPreparationPlanEnvelopeFactory factory =
            new OpsPreparationPlanEnvelopeFactory();

    @Test
    void acceptableAssessmentCreatesSingleExecutableCandidateAndFrozenBoundary() {
        Map<String, Object> read = step(
                "op-read",
                "query_health",
                "mcp-1",
                "READ_EXTERNAL_STATE",
                "PLATFORM_INTERNAL");
        Map<String, Object> sandbox = step(
                "op-sandbox",
                "validate_patch",
                "mcp-2",
                "MUTATE_TEMP_RESOURCE",
                "SANDBOX");

        OpsPreparationPlanEnvelopeFactory.Envelope envelope = factory.create(
                input(
                        "ACCEPTABLE",
                        "HIGH",
                        Map.of("maxRiskLevel", "LOW"),
                        List.of(read, sandbox)));

        assertEquals(1, envelope.candidatePlans().size());
        assertEquals("candidate-1", envelope.preferredPlan().get("selectedCandidateId"));
        assertEquals("HIGH", envelope.approvalBoundary().get("maxRiskLevel"));
        assertEquals(
                List.of("READ_EXTERNAL_STATE", "MUTATE_EPHEMERAL"),
                envelope.approvalBoundary().get("allowedEffectTypes"));
        assertTrue(((List<?>) envelope.approvalBoundary().get("allowedEffectScopes"))
                .contains("SANDBOX"));
        assertEquals(
                List.of("query_health", "mcp-1", "validate_patch", "mcp-2"),
                envelope.allowedTools());
        assertEquals("HIGH", envelope.adjustmentPolicy().get("maxRiskLevelAfterAdjustment"));
        assertTrue(envelope.replanTriggers().contains("POLICY_STALE_OR_UNKNOWN"));
    }

    @Test
    void nonAcceptableAssessmentSelectsManualCandidateWhenIterationBudgetAllows() {
        OpsPreparationPlanEnvelopeFactory.Envelope envelope = factory.create(
                new OpsPreparationPlanEnvelopeFactory.Input(
                        "repair service",
                        2,
                        "NEEDS_REFINEMENT",
                        Map.of(),
                        List.of(),
                        List.of("dry run missing"),
                        "MEDIUM"));

        assertEquals(2, envelope.candidatePlans().size());
        assertEquals("MANUAL_REQUIRED", envelope.candidatePlans().get(1).get("status"));
        assertEquals("candidate-2", envelope.preferredPlan().get("selectedCandidateId"));
        assertEquals(
                List.of("READ_EXTERNAL_STATE", "VALIDATE_ONLY", "DRY_RUN"),
                envelope.approvalBoundary().get("allowedEffectTypes"));
    }

    @Test
    void providedPlanViewsAndAdjustmentsAreCopiedAndNormalized() {
        Map<String, Object> candidate = new LinkedHashMap<>(Map.of(
                "candidateId", "custom-candidate",
                "status", "DRAFTED"));
        Map<String, Object> boundary = new LinkedHashMap<>(Map.of(
                "maxRiskLevel", "CRITICAL"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("candidatePlans", List.of(candidate));
        request.put("approvalBoundary", boundary);
        request.put("preferredPlan", Map.of("selectedCandidateId", "custom-candidate"));
        request.put("adjustmentPolicy", Map.of("allowRetry", false));
        request.put("allowedLandingAdjustments", List.of(
                "ARGUMENT_CHANGE",
                Map.of("type", "REORDER_INDEPENDENT_STEPS", "requiresRetest", false),
                Map.of("ignored", true)));

        OpsPreparationPlanEnvelopeFactory.Envelope envelope = factory.create(
                input("ACCEPTABLE", "LOW", request, List.of()));
        candidate.put("status", "MUTATED_AFTER_CREATE");
        boundary.put("maxRiskLevel", "LOW");

        assertEquals("DRAFTED", envelope.candidatePlans().get(0).get("status"));
        assertEquals("CRITICAL", envelope.approvalBoundary().get("maxRiskLevel"));
        assertEquals(false, envelope.adjustmentPolicy().get("allowRetry"));
        assertEquals(2, envelope.allowedLandingAdjustments().size());
        assertEquals(
                "APPROVED_CHANGED_FILES_ONLY",
                envelope.allowedLandingAdjustments().get(0).get("allowedFileScope"));
        assertFalse(envelope.allowedLandingAdjustments().stream()
                .anyMatch(item -> item.containsKey("ignored")));
    }

    private OpsPreparationPlanEnvelopeFactory.Input input(
            String assessment,
            String riskLevel,
            Map<String, Object> request,
            List<Map<String, Object>> steps) {
        return new OpsPreparationPlanEnvelopeFactory.Input(
                "repair service",
                2,
                assessment,
                request,
                steps,
                List.of("limitation-1"),
                riskLevel);
    }

    private Map<String, Object> step(String operationId,
                                     String toolName,
                                     String mcpId,
                                     String effectType,
                                     String effectScope) {
        return Map.of(
                "operationId", operationId,
                "toolName", toolName,
                "mcpId", mcpId,
                "effectType", effectType,
                "effectScope", effectScope);
    }
}
