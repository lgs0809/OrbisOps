package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCandidateTournamentRolloutCoordinatorTest {

    private static final String BASE_HASH = "a".repeat(64);
    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("structural-v1", "behavior-v1", "judge-v1");

    @Test
    void strictShadowMustKeepLegacyWhenTargetIsMissing() {
        RecordingAudit audit = new RecordingAudit();
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_SHADOW,
                passingTournament(),
                (projectId, candidate) -> SkillEvolutionSimilarityMatch.none(),
                audit);
        SkillCandidateSetSelectionResult legacy = legacySelection();
        SkillEvolutionSimilarityMatch missing = SkillEvolutionSimilarityMatch.none();

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacy, missing);

        assertSame(legacy, outcome.selection());
        assertSame(missing, outcome.similar());
        assertTrue(outcome.blockingReason().isBlank());
        assertEquals("NOT_APPLICABLE", outcome.details().get("status"));
        assertEquals("SKILL_STRICT_TARGET_REQUIRED", outcome.details().get("reasonCode"));
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictShadowMustKeepLegacyWhenTournamentIsUnavailable() {
        RecordingAudit audit = new RecordingAudit();
        SkillCandidateTournament tournament = tournament(
                SkillModelJudgeDisposition.PASS,
                true);
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_SHADOW,
                tournament,
                sameTarget(),
                audit);
        SkillCandidateSetSelectionResult legacy = legacySelection();

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacy, target());

        assertSame(legacy, outcome.selection());
        assertTrue(outcome.blockingReason().isBlank());
        assertEquals("STRICT_UNAVAILABLE", outcome.details().get("status"));
        assertEquals("SKILL_HIDDEN_SUITE_MISSING", outcome.details().get("reasonCode"));
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustBlockWhenTournamentIsUnavailable() {
        RecordingAudit audit = new RecordingAudit();
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                tournament(SkillModelJudgeDisposition.PASS, true),
                sameTarget(),
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertEquals("SKIP_STRICT_TOURNAMENT_UNAVAILABLE", outcome.blockingReason());
        assertEquals("STRICT_UNAVAILABLE", outcome.details().get("status"));
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustBlockManualReview() {
        RecordingAudit audit = new RecordingAudit();
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                tournament(SkillModelJudgeDisposition.MANUAL_REVIEW, false),
                sameTarget(),
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertEquals("SKIP_STRICT_TOURNAMENT_MANUAL_REVIEW", outcome.blockingReason());
        assertEquals("MANUAL_REVIEW_REQUIRED", outcome.details().get("status"));
        assertFalse(outcome.selection().selected());
        assertTrue(outcome.selection().tournamentResult().manualReviewRequired());
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustBlockWhenNoCandidateIsEligible() {
        RecordingAudit audit = new RecordingAudit();
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                tournament(SkillModelJudgeDisposition.FAIL, false),
                sameTarget(),
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertEquals("SKIP_STRICT_TOURNAMENT_NO_ELIGIBLE_CANDIDATE", outcome.blockingReason());
        assertEquals("NO_ELIGIBLE_CANDIDATE", outcome.details().get("status"));
        assertFalse(outcome.selection().selected());
        assertFalse(outcome.selection().tournamentResult().manualReviewRequired());
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustBlockTargetDrift() {
        RecordingAudit audit = new RecordingAudit();
        SkillEvolutionSimilarityMatch drifted = new SkillEvolutionSimilarityMatch(
                "skill-2", 7, "b".repeat(64), 0.97D, false, "drift");
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                passingTournament(),
                (projectId, candidate) -> drifted,
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertEquals("SKIP_STRICT_TOURNAMENT_TARGET_DRIFT", outcome.blockingReason());
        assertEquals("TARGET_DRIFT", outcome.details().get("status"));
        assertSame(drifted, outcome.similar());
        assertEquals("skill-2", outcome.details().get("strictTargetSkillId"));
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustBlockFrozenTarget() {
        RecordingAudit audit = new RecordingAudit();
        SkillEvolutionSimilarityMatch frozen = new SkillEvolutionSimilarityMatch(
                "skill-1", 7, BASE_HASH, 0.97D, true, "frozen");
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                passingTournament(),
                (projectId, candidate) -> frozen,
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertEquals("SKIP_STRICT_TOURNAMENT_TARGET_FROZEN", outcome.blockingReason());
        assertEquals("TARGET_FROZEN", outcome.details().get("status"));
        assertSame(frozen, outcome.similar());
        assertEquals(1, audit.comparisons.size());
    }

    @Test
    void strictPrimaryMustSelectWinnerOnlyWhenTargetRemainsStable() {
        RecordingAudit audit = new RecordingAudit();
        SkillEvolutionSimilarityMatch stable = target();
        SkillCandidateTournamentRolloutCoordinator coordinator = coordinator(
                SkillCandidateTournamentRolloutMode.STRICT_PRIMARY,
                passingTournament(),
                (projectId, candidate) -> stable,
                audit);

        SkillCandidateTournamentRolloutCoordinator.Outcome outcome = coordinator.select(
                request(), options(), legacySelection(), target());

        assertTrue(outcome.blockingReason().isBlank());
        assertEquals("STRICT_PRIMARY_SELECTED", outcome.details().get("status"));
        assertEquals(SkillCandidateSelectionMode.STRICT_TOURNAMENT, outcome.selection().mode());
        assertEquals("candidate-1", outcome.selection().selectedCandidate().payload().get("name"));
        assertSame(stable, outcome.similar());
        assertEquals(1, audit.comparisons.size());
        assertEquals("STRICT_PRIMARY", audit.comparisons.get(0).rolloutMode());
    }

    private SkillCandidateTournamentRolloutCoordinator coordinator(
            SkillCandidateTournamentRolloutMode mode,
            SkillCandidateTournament tournament,
            SkillEvolutionSimilarityPort similarity,
            RecordingAudit audit) {
        return new SkillCandidateTournamentRolloutCoordinator(
                new SkillEvolutionCandidateSetSelector(tournament),
                similarity,
                audit,
                new SkillCandidateTournamentSettings(mode, "v1", VERSION));
    }

    private SkillCandidateTournament passingTournament() {
        return tournament(SkillModelJudgeDisposition.PASS, false);
    }

    private SkillCandidateTournament tournament(
            SkillModelJudgeDisposition disposition,
            boolean hiddenUnavailable) {
        return new SkillCandidateTournament(
                (candidate, version) -> new SkillStructuralVerification(
                        candidate.candidateId(), true, 0.9D, List.of(),
                        version.structuralVersion()),
                (skillId, baseVersion, candidate) -> {
                    if (hiddenUnavailable) {
                        throw new IllegalStateException("SKILL_HIDDEN_SUITE_MISSING");
                    }
                    return new SkillHiddenEvaluationSet(
                            "suite-v1",
                            List.of(Map.of("caseId", "hidden-1")),
                            List.of(Map.of("caseId", "mutation-1")),
                            CanonicalObjectHasher.sha256("hidden"),
                            CanonicalObjectHasher.sha256("mutation"));
                },
                (candidate, evaluationSet, version) -> behavior(
                        candidate.candidateHash(),
                        candidate.candidateId().equals("candidate-1") ? 0.95D : 0.85D,
                        version.behaviorVersion()),
                (candidate, behavior, evaluationSet, version) -> new SkillModelJudgeEvaluation(
                        candidate.candidateId(),
                        disposition,
                        disposition == SkillModelJudgeDisposition.PASS ? 0.9D : 0D,
                        disposition == SkillModelJudgeDisposition.PASS
                                ? List.of()
                                : List.of("judge-" + disposition.name().toLowerCase()),
                        version.judgeVersion()));
    }

    private SkillBehaviorEvaluation behavior(
            String candidateHash,
            double success,
            String verifierVersion) {
        return new SkillBehaviorEvaluation(
                "evaluation-" + candidateHash,
                replay(SkillBehaviorReplayArm.NO_SKILL, "", 0.2D),
                replay(SkillBehaviorReplayArm.BASELINE, "baseline-hash", 0.6D),
                replay(SkillBehaviorReplayArm.CANDIDATE, candidateHash, success),
                true,
                List.of(),
                verifierVersion);
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArm arm,
            String activeHash,
            double success) {
        return new SkillBehaviorReplayResult(
                arm,
                activeHash,
                new SkillBehaviorMetrics(
                        success, 0, 1, 0, 0.8D, 0.01D,
                        100L, 200L, 500L, 0.8D, 0.8D),
                CanonicalObjectHasher.sha256(arm.name() + ":answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence"),
                List.of(),
                List.of());
    }

    private SkillCandidateSetSelectionResult legacySelection() {
        return new SkillEvolutionCandidateSetSelector().select(
                SkillCandidateSelectionMode.LEGACY_COMPATIBILITY,
                options(),
                null);
    }

    private List<SkillEvolutionAuthoredCandidateOption> options() {
        return List.of(option(0), option(1));
    }

    private SkillEvolutionAuthoredCandidateOption option(int index) {
        Map<String, Object> payload = Map.of(
                "name", "candidate-" + index,
                "patchType", "PATCH",
                "changes", List.of(Map.of(
                        "operation", "REPLACE",
                        "path", "/procedure",
                        "value", "candidate-" + index)),
                "authoringSource", "TEST");
        return new SkillEvolutionAuthoredCandidateOption(
                SkillEvolutionAuthoredCandidate.from(payload),
                new SkillEvolutionAuthoringAudit(
                        "model-1", "prompt-v1", 42L,
                        "input-hash", 2, index,
                        SkillAuthoringDirection.values()[index]));
    }

    private SkillEvolutionPipelineRequest request() {
        return new SkillEvolutionPipelineRequest(
                "project-1",
                "agent-1",
                "run-1",
                "session-1",
                "RUN_COMPLETED",
                new SkillEvolutionInputSummary(
                        List.of("completed"),
                        List.of("tool"),
                        "goal",
                        "report",
                        true,
                        true,
                        true,
                        List.of(),
                        "context-hash"));
    }

    private SkillEvolutionSimilarityMatch target() {
        return new SkillEvolutionSimilarityMatch(
                "skill-1", 7, BASE_HASH, 0.97D, false, "matched");
    }

    private SkillEvolutionSimilarityPort sameTarget() {
        return (projectId, candidate) -> target();
    }

    private static final class RecordingAudit implements SkillEvolutionPipelineAuditPort {
        private final List<Comparison> comparisons = new ArrayList<>();

        @Override
        public void recordSkipped(
                String projectId,
                String agentId,
                String runId,
                String reasonCode,
                Map<String, Object> details) {
        }

        @Override
        public void recordCandidateCreated(
                String projectId,
                String agentId,
                String candidateId,
                String releaseStatus,
                Map<String, Object> details) {
        }

        @Override
        public void recordCandidateSelectionCompared(
                String projectId,
                String agentId,
                String runId,
                String rolloutMode,
                Map<String, Object> details) {
            comparisons.add(new Comparison(
                    projectId, agentId, runId, rolloutMode, details));
        }
    }

    private record Comparison(
            String projectId,
            String agentId,
            String runId,
            String rolloutMode,
            Map<String, Object> details) {
    }
}
