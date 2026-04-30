package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationStatus;
import cn.lgs.orbisops.domain.skill.service.SkillOptimizationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillOptimizationPolicyTest {

    private final SkillOptimizationPolicy policy = new SkillOptimizationPolicy();
    private final Instant now = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void unsuccessfulRoundsMustStopAtConfiguredLimit() {
        SkillOptimizationRun run = policy.start(
                "run-1", "skill-1", 7, "skill-hash", 2, now);
        run = failedRound(run, 1);
        assertEquals(SkillOptimizationStatus.RUNNING, run.status());
        run = failedRound(run, 2);

        assertEquals(SkillOptimizationStatus.ROUND_LIMIT_REACHED, run.status());
        SkillOptimizationRun terminal = run;
        assertThrows(IllegalStateException.class, () -> policy.startRound(
                terminal, List.of("diagnosis-3"), now.plusSeconds(20)));
    }

    @Test
    void activeRoundAndUnknownSelectedCandidateMustFailClosed() {
        SkillOptimizationRun run = policy.start(
                "run-1", "skill-1", 7, "skill-hash", 3, now);
        run = policy.startRound(run, List.of("diagnosis-1"), now.plusSeconds(1));
        SkillOptimizationRun active = run;
        assertThrows(IllegalStateException.class, () -> policy.startRound(
                active, List.of("diagnosis-2"), now.plusSeconds(2)));

        run = policy.beginEvaluation(run, List.of("candidate-a", "candidate-b"), now.plusSeconds(3));
        SkillOptimizationRun evaluating = run;
        assertThrows(IllegalArgumentException.class, () -> policy.completeRound(
                evaluating, List.of("evaluation-1"), "candidate-x", "verifier-v1",
                "SUCCESS", true, now.plusSeconds(4)));
    }

    @Test
    void successfulRoundMustFreezeSelectedCandidateAndVerifier() {
        SkillOptimizationRun run = policy.start(
                "run-1", "skill-1", 7, "skill-hash", 3, now);
        run = policy.startRound(run, List.of("diagnosis-1"), now.plusSeconds(1));
        run = policy.beginEvaluation(run, List.of("candidate-a", "candidate-b"), now.plusSeconds(2));
        run = policy.completeRound(
                run, List.of("evaluation-1", "evaluation-2"), "candidate-b",
                "verifier-v2", "PROMOTABLE", true, now.plusSeconds(3));

        assertEquals(SkillOptimizationStatus.SUCCEEDED, run.status());
        assertEquals("candidate-b", run.rounds().get(0).selectedCandidateId());
        assertEquals("verifier-v2", run.rounds().get(0).verifierVersion());
        assertEquals(List.of("evaluation-1", "evaluation-2"),
                run.rounds().get(0).evaluationIds());
    }

    private SkillOptimizationRun failedRound(SkillOptimizationRun run, int round) {
        SkillOptimizationRun started = policy.startRound(
                run, List.of("diagnosis-" + round), now.plusSeconds(round * 3L));
        SkillOptimizationRun evaluating = policy.beginEvaluation(
                started, List.of("candidate-" + round), now.plusSeconds(round * 3L + 1));
        return policy.completeRound(
                evaluating, List.of("evaluation-" + round), "", "verifier-v1",
                "NO_IMPROVEMENT", false, now.plusSeconds(round * 3L + 2));
    }
}
