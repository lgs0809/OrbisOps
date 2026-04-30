package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillCandidateTournamentContext;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillPatchRegressionResult;
import cn.lgs.orbisops.application.skill.SkillTournamentCandidate;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSkillTournamentStructuralVerifierAdapterTest {

    private static final String BASE_HASH = "a".repeat(64);
    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("structural-v1", "behavior-v1", "judge-v1");

    @Test
    void validCandidateMustPassDeterministicRegression() {
        OpsSkillTournamentStructuralVerifierAdapter adapter = adapter(passRegression());

        SkillStructuralVerification result = adapter.verify(context(), candidate(validPayload()));

        assertTrue(result.passed());
        assertEquals(1D, result.score());
        assertTrue(result.reasonCodes().isEmpty());
        assertEquals("structural-v1", result.verifierVersion());
    }

    @Test
    void candidateHashMismatchMustFailBeforeRegression() {
        OpsSkillTournamentStructuralVerifierAdapter adapter = adapter(input -> {
            throw new AssertionError("regression must not run");
        });
        SkillTournamentCandidate candidate = candidate(validPayload(), "b".repeat(64));

        SkillStructuralVerification result = adapter.verify(context(), candidate);

        assertReason(result, "SKILL_STRUCTURAL_CANDIDATE_HASH_MISMATCH");
    }

    @Test
    void missingSecurityBoundaryMustFailClosed() {
        Map<String, Object> payload = validPayload();
        payload.remove("securityBoundary");

        assertReason(verify(payload), "SKILL_STRUCTURAL_SECURITY_BOUNDARY_INVALID");
    }

    @Test
    void productionWriteMustBeForbidden() {
        Map<String, Object> payload = validPayload();
        security(payload).put("productionWriteAllowed", true);

        assertReason(verify(payload), "SKILL_STRUCTURAL_SECURITY_BOUNDARY_INVALID");
    }

    @Test
    void changePackageOnlyMustBeRequired() {
        Map<String, Object> payload = validPayload();
        security(payload).put("changePackageOnly", false);

        assertReason(verify(payload), "SKILL_STRUCTURAL_SECURITY_BOUNDARY_INVALID");
    }

    @Test
    void directLandingMustBeForbidden() {
        Map<String, Object> payload = validPayload();
        tools(payload).put("directLandingAllowed", true);

        assertReason(verify(payload), "SKILL_STRUCTURAL_TOOL_BOUNDARY_INVALID");
    }

    @Test
    void unsafeToolExecutionModeMustFail() {
        Map<String, Object> payload = validPayload();
        tools(payload).put("executionMode", "PRODUCTION_WRITE");

        assertReason(verify(payload), "SKILL_STRUCTURAL_TOOL_BOUNDARY_INVALID");
    }

    @Test
    void duplicateChangeIdentityMustFail() {
        Map<String, Object> payload = validPayload();
        payload.put("changes", List.of(
                change("procedure", "step-1"),
                change("procedure", "step-1")));

        assertReason(verify(payload), "SKILL_STRUCTURAL_CHANGE_DUPLICATE:procedure:step-1");
    }

    @Test
    void duplicateArtifactPathMustFail() {
        Map<String, Object> payload = validPayload();
        payload.put("artifacts", List.of(
                Map.of("path", "skills/skill-1.md"),
                Map.of("path", "skills/skill-1.md")));

        assertReason(verify(payload), "SKILL_STRUCTURAL_ARTIFACT_DUPLICATE:skills/skill-1.md");
    }

    @Test
    void optionalTargetSkillMustMatchFrozenContext() {
        Map<String, Object> payload = validPayload();
        payload.put("targetSkillId", "skill-2");

        assertReason(verify(payload), "SKILL_STRUCTURAL_TARGET_SKILL_MISMATCH");
    }

    @Test
    void optionalBaseVersionMustMatchFrozenContext() {
        Map<String, Object> payload = validPayload();
        payload.put("baseSkillVersion", 8);

        assertReason(verify(payload), "SKILL_STRUCTURAL_BASE_VERSION_MISMATCH");
    }

    @Test
    void optionalBaseHashMustMatchFrozenContext() {
        Map<String, Object> payload = validPayload();
        payload.put("baseSkillHash", "b".repeat(64));

        assertReason(verify(payload), "SKILL_STRUCTURAL_BASE_HASH_MISMATCH");
    }

    @Test
    void deterministicRegressionFailureMustBePreserved() {
        OpsSkillTournamentStructuralVerifierAdapter adapter = adapter(input ->
                new SkillPatchRegressionResult(
                        false,
                        List.of("SKILL_REGRESSION_CASE_FAILED"),
                        List.of(),
                        Map.of(),
                        3));

        SkillStructuralVerification result = adapter.verify(context(), candidate(validPayload()));

        assertReason(result, "SKILL_REGRESSION_CASE_FAILED");
    }

    @Test
    void regressionPortFailureMustFailClosed() {
        OpsSkillTournamentStructuralVerifierAdapter adapter = adapter(input -> {
            throw new IllegalStateException("offline");
        });

        SkillStructuralVerification result = adapter.verify(context(), candidate(validPayload()));

        assertReason(result, "SKILL_STRUCTURAL_REGRESSION_UNAVAILABLE:IllegalStateException");
    }

    private SkillStructuralVerification verify(Map<String, Object> payload) {
        return adapter(passRegression()).verify(context(), candidate(payload));
    }

    private OpsSkillTournamentStructuralVerifierAdapter adapter(
            cn.lgs.orbisops.application.skill.SkillPatchRegressionEvaluationPort regression) {
        return new OpsSkillTournamentStructuralVerifierAdapter(regression);
    }

    private cn.lgs.orbisops.application.skill.SkillPatchRegressionEvaluationPort passRegression() {
        return input -> new SkillPatchRegressionResult(
                true, List.of(), List.of(), Map.of("passed", true), 2);
    }

    private SkillCandidateTournamentContext context() {
        return new SkillCandidateTournamentContext(
                "tournament-1",
                "project-1",
                "skill-1",
                7,
                BASE_HASH,
                "suite-v1",
                VERSION);
    }

    private SkillTournamentCandidate candidate(Map<String, Object> payload) {
        return candidate(payload, CanonicalObjectHasher.sha256(payload));
    }

    private SkillTournamentCandidate candidate(
            Map<String, Object> payload,
            String candidateHash) {
        SkillEvolutionAuthoredCandidate authored = SkillEvolutionAuthoredCandidate.from(payload);
        return new SkillTournamentCandidate(
                "candidate-1", candidateHash, authored, authored.changes().size());
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patchType", "PATCH");
        payload.put("changes", new ArrayList<>(List.of(change("procedure", "step-1"))));
        payload.put("artifacts", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("path", "skills/skill-1.md")))));
        payload.put("evalCases", new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("caseId", "eval-1", "input", "diagnose")))));
        payload.put("authoringSource", "TEST");
        payload.put("targetSkillId", "skill-1");
        payload.put("baseSkillVersion", 7);
        payload.put("baseSkillHash", BASE_HASH);
        payload.put("securityBoundary", new LinkedHashMap<>(Map.of(
                "productionWriteAllowed", false,
                "changePackageOnly", true)));
        payload.put("toolBoundary", new LinkedHashMap<>(Map.of(
                "directLandingAllowed", false,
                "executionMode", "SANDBOX_DRY_RUN")));
        return payload;
    }

    private Map<String, Object> change(String section, String key) {
        return new LinkedHashMap<>(Map.of(
                "section", section,
                "key", key,
                "operation", "REPLACE",
                "value", "updated"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> security(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("securityBoundary");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tools(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("toolBoundary");
    }

    private void assertReason(
            SkillStructuralVerification result,
            String reason) {
        assertFalse(result.passed());
        assertTrue(result.reasonCodes().contains(reason),
                () -> "missing reason " + reason + " in " + result.reasonCodes());
    }
}
