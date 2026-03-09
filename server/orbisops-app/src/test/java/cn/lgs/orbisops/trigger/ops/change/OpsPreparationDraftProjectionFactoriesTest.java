package cn.lgs.orbisops.trigger.ops.change;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsPreparationDraftProjectionFactoriesTest {

    @Test
    void contextProjectionSeparatesPackageAndEvidenceViews() {
        OpsPreparationContextProjectionFactory factory =
                new OpsPreparationContextProjectionFactory();
        Map<String, Object> bundle = Map.ofEntries(
                Map.entry("contextBundleId", "ctx-1"),
                Map.entry("contextBundleHash", "ctx-hash"),
                Map.entry("memoryContextRefs", List.of("project:project-1")),
                Map.entry("memoryContextHash", "memory-hash"),
                Map.entry("compressedMemorySummary", "summary"),
                Map.entry("memoryInjectionVersion", "memory-v1"),
                Map.entry("usedSkillVersionRefs", List.of(Map.of(
                        "skillId", "skill-a",
                        "version", 2,
                        "skillHash", "skill-hash"))),
                Map.entry("usedSkillRefsHash", "skill-refs-hash"),
                Map.entry("toolsetRefs", List.of("toolset-a")),
                Map.entry("policyRefs", List.of("policy-a")),
                Map.entry("policyHash", "policy-hash"),
                Map.entry("toolsetBoundaryHash", "toolset-hash"),
                Map.entry("runtimeBoundaryHash", "runtime-hash"),
                Map.entry("approvalBoundaryHash", "approval-hash"));

        Map<String, Object> packageContext = factory.packageContext(bundle);
        Map<String, Object> evidenceContext = factory.evidenceContext(bundle);

        assertEquals("memory-v1", packageContext.get("memoryInjectionVersion"));
        assertEquals("approval-hash", packageContext.get("contextApprovalBoundaryHash"));
        assertFalse(evidenceContext.containsKey("memoryInjectionVersion"));
        assertFalse(evidenceContext.containsKey("contextApprovalBoundaryHash"));
        assertEquals(List.of("skill-a"), factory.skillRefValues(bundle, "skillId"));
        assertEquals(List.of(2), factory.skillRefValues(bundle, "version"));
        assertEquals(List.of("skill-hash"), factory.skillRefValues(bundle, "skillHash"));
    }

    @Test
    void landingProjectionKeepsEnvelopeContract() {
        OpsPreparationPlanEnvelopeFactory.Envelope envelope =
                new OpsPreparationPlanEnvelopeFactory.Envelope(
                        List.of(Map.of("candidateId", "candidate-1")),
                        Map.of("maxRiskLevel", "HIGH"),
                        Map.of("selectedCandidateId", "candidate-1"),
                        Map.of("allowRetry", true),
                        List.of(Map.of("type", "ARGUMENT_CHANGE")),
                        List.of("query_health"),
                        List.of("POLICY_STALE_OR_UNKNOWN"));

        Map<String, Object> plan =
                new OpsPreparationLandingPlanProjectionFactory().create(envelope);

        assertEquals(envelope.approvalBoundary(), plan.get("approvalBoundary"));
        assertEquals(envelope.preferredPlan(), plan.get("preferredPlan"));
        assertEquals(envelope.adjustmentPolicy(), plan.get("adjustmentPolicy"));
        assertEquals(envelope.replanTriggers(), plan.get("replanTriggers"));
    }

    @Test
    void riskEscalationAuditPayloadIsStableAndImmutable() {
        OpsPreparationAuditPayloadFactory.AuditPayload payload =
                new OpsPreparationAuditPayloadFactory().riskEscalated(
                        Map.of("sessionId", "session-1", "riskLevel", "LOW"),
                        "HIGH");

        assertEquals("session-1", payload.targetId());
        assertEquals(Map.of("requestedRiskLevel", "LOW"), payload.before());
        assertEquals(Map.of(
                "systemRiskLevel", "HIGH",
                "reason", "REQUEST_RISK_LOWER_THAN_SYSTEM_ASSESSMENT"), payload.after());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.after().put("reason", "CHANGED"));
    }
}
