package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateVerification;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCandidateSelectionPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SkillCandidateSelectionPolicyTest {

    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("s1", "b1", "j1");
    private final SkillCandidateSelectionPolicy policy =
            new SkillCandidateSelectionPolicy();

    @Test
    void safetyAndVerifierEligibilityMustOutrankJudgeScore() {
        SkillCandidateVerification unsafeHighJudge = candidate(
                "unsafe", 0.99, 0.95, 1, 0.95, 0.95, 500, 1);
        SkillCandidateVerification safeLowerJudge = candidate(
                "safe", 0.70, 0.82, 0, 0.82, 0.84, 900, 2);

        SkillCandidateTournamentResult result = policy.select(
                "tournament-1", List.of(unsafeHighJudge, safeLowerJudge), VERSION);

        assertEquals("safe", result.selectedCandidateId());
        assertEquals("safe", result.rankings().get(0).candidateId());
        assertFalse(unsafeHighJudge.eligible());
    }

    @Test
    void successRoutingEvidenceCostComplexityAndIdMustProduceStableRanking() {
        List<SkillCandidateVerification> source = List.of(
                candidate("candidate-c", 0.80, 0.85, 0, 0.82, 0.84, 800, 3),
                candidate("candidate-a", 0.80, 0.85, 0, 0.82, 0.84, 800, 2),
                candidate("candidate-b", 0.80, 0.85, 0, 0.82, 0.84, 800, 2));
        List<String> expected = List.of("candidate-a", "candidate-b", "candidate-c");

        for (int seed = 0; seed < 20; seed++) {
            List<SkillCandidateVerification> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, new Random(seed));
            SkillCandidateTournamentResult result = policy.select(
                    "tournament-1", shuffled, VERSION);
            assertEquals(expected,
                    result.rankings().stream().map(SkillCandidateVerification::candidateId).toList());
            assertEquals("candidate-a", result.selectedCandidateId());
        }
    }

    @Test
    void deterministicFailureMustRemainIneligibleEvenWithJudgePass() {
        SkillCandidateVerification failedBehavior = candidate(
                "failed", 0.99, 0.99, 0, 0.99, 0.99, 100, 1,
                false, SkillModelJudgeDisposition.PASS);
        SkillCandidateTournamentResult result = policy.select(
                "tournament-1", List.of(failedBehavior), VERSION);

        assertFalse(result.selected());
        assertEquals(List.of("SKILL_TOURNAMENT_NO_ELIGIBLE_CANDIDATE"),
                result.reasonCodes());
    }

    private SkillCandidateVerification candidate(
            String id,
            double structuralScore,
            double success,
            int safetyViolations,
            double routing,
            double evidence,
            long cost,
            int complexity) {
        return candidate(id, structuralScore, success, safetyViolations,
                routing, evidence, cost, complexity, true,
                SkillModelJudgeDisposition.PASS);
    }

    private SkillCandidateVerification candidate(
            String id,
            double structuralScore,
            double success,
            int safetyViolations,
            double routing,
            double evidence,
            long cost,
            int complexity,
            boolean admitted,
            SkillModelJudgeDisposition disposition) {
        String hash = CanonicalObjectHasher.sha256(id);
        SkillStructuralVerification structural = new SkillStructuralVerification(
                id, true, structuralScore, List.of(), VERSION.structuralVersion());
        SkillBehaviorEvaluation behavior = new SkillBehaviorEvaluation(
                "evaluation-" + id,
                replay(SkillBehaviorReplayArm.NO_SKILL, "", 0.2, 0, 0.4, 0.4, 500),
                replay(SkillBehaviorReplayArm.BASELINE, "baseline", 0.6, 0, 0.7, 0.7, 900),
                replay(SkillBehaviorReplayArm.CANDIDATE, hash, success,
                        safetyViolations, evidence, routing, cost),
                admitted,
                admitted ? List.of() : List.of("DETERMINISTIC_FAIL"),
                VERSION.behaviorVersion());
        SkillModelJudgeEvaluation judge = new SkillModelJudgeEvaluation(
                id, disposition, 0.99,
                disposition == SkillModelJudgeDisposition.PASS
                        ? List.of() : List.of("JUDGE_REVIEW"),
                VERSION.judgeVersion());
        return new SkillCandidateVerification(
                id, hash, structural, behavior, judge,
                CanonicalObjectHasher.sha256(id + ":hidden"),
                CanonicalObjectHasher.sha256(id + ":mutation"), complexity);
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArm arm,
            String activeHash,
            double success,
            int safety,
            double evidence,
            double routing,
            long cost) {
        return new SkillBehaviorReplayResult(
                arm, activeHash,
                new SkillBehaviorMetrics(
                        success, safety, 2, 0, evidence, 0.01,
                        100, 200, cost, evidence, routing),
                CanonicalObjectHasher.sha256(arm.name() + ":answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence"),
                List.of(), List.of());
    }
}
