package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCandidateTournamentTest {

    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("struct-v1", "behavior-v2", "judge-v3");

    @Test
    void tournamentMustShortCircuitInStrictVerifierOrder() {
        List<String> calls = new ArrayList<>();
        SkillCandidateTournament tournament = new SkillCandidateTournament(
                (candidate, version) -> {
                    calls.add("S:" + candidate.candidateId());
                    boolean passed = !candidate.candidateId().equals("candidate-a");
                    return structural(candidate.candidateId(), passed);
                },
                (skillId, baseVersion, candidate) -> {
                    calls.add("H:" + candidate.candidateId());
                    return hidden(candidate.candidateId());
                },
                (candidate, evaluationSet, version) -> {
                    calls.add("B:" + candidate.candidateId());
                    return behavior(candidate, !candidate.candidateId().equals("candidate-b"));
                },
                (candidate, behavior, evaluationSet, version) -> {
                    calls.add("J:" + candidate.candidateId());
                    return judge(candidate.candidateId(), SkillModelJudgeDisposition.PASS, 0.9);
                });

        SkillCandidateTournamentResult result = tournament.run(
                "tournament-1", "skill-1", 7,
                List.of(candidate("candidate-c", 3), candidate("candidate-a", 1),
                        candidate("candidate-b", 2)), VERSION);

        assertEquals(List.of(
                "S:candidate-a",
                "S:candidate-b", "H:candidate-b", "B:candidate-b",
                "S:candidate-c", "H:candidate-c", "B:candidate-c", "J:candidate-c"), calls);
        assertEquals("candidate-c", result.selectedCandidateId());
        assertFalse(result.manualReviewRequired());
        assertEquals(3, result.rankings().size());
        assertFalse(result.rankings().stream()
                .filter(item -> item.candidateId().equals("candidate-b"))
                .findFirst().orElseThrow().eligible());
    }

    @Test
    void judgeFailureOrUnavailableMustRequireManualReview() {
        SkillCandidateTournament tournament = new SkillCandidateTournament(
                (candidate, version) -> structural(candidate.candidateId(), true),
                (skillId, baseVersion, candidate) -> hidden(candidate.candidateId()),
                (candidate, evaluationSet, version) -> behavior(candidate, true),
                (candidate, behavior, evaluationSet, version) -> {
                    if (candidate.candidateId().equals("candidate-a")) {
                        throw new IllegalStateException("judge offline");
                    }
                    return judge(candidate.candidateId(),
                            SkillModelJudgeDisposition.MANUAL_REVIEW, 0.7);
                });

        SkillCandidateTournamentResult result = tournament.run(
                "tournament-1", "skill-1", 7,
                List.of(candidate("candidate-a", 1), candidate("candidate-b", 2)), VERSION);

        assertFalse(result.selected());
        assertTrue(result.manualReviewRequired());
        assertEquals(List.of("SKILL_TOURNAMENT_MANUAL_REVIEW_REQUIRED"), result.reasonCodes());
        assertTrue(result.rankings().stream().allMatch(item -> !item.eligible()));
    }

    @Test
    void shuffledInputMustProduceIdenticalStableRanking() {
        SkillCandidateTournament tournament = new SkillCandidateTournament(
                (candidate, version) -> structural(candidate.candidateId(), true),
                (skillId, baseVersion, candidate) -> hidden(candidate.candidateId()),
                (candidate, evaluationSet, version) -> behavior(candidate, true),
                (candidate, behavior, evaluationSet, version) -> judge(
                        candidate.candidateId(), SkillModelJudgeDisposition.PASS, 0.8));
        List<SkillTournamentCandidate> source = List.of(
                candidate("candidate-c", 3),
                candidate("candidate-a", 1),
                candidate("candidate-b", 2));
        SkillCandidateTournamentResult expected = tournament.run(
                "tournament-1", "skill-1", 7, source, VERSION);

        for (int seed = 0; seed < 20; seed++) {
            List<SkillTournamentCandidate> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, new Random(seed));
            SkillCandidateTournamentResult actual = tournament.run(
                    "tournament-1", "skill-1", 7, shuffled, VERSION);
            assertEquals(expected.selectedCandidateId(), actual.selectedCandidateId());
            assertEquals(expected.rankings().stream().map(item -> item.candidateId()).toList(),
                    actual.rankings().stream().map(item -> item.candidateId()).toList());
        }
        assertEquals("candidate-a", expected.selectedCandidateId());
    }

    private SkillTournamentCandidate candidate(String id, int complexity) {
        return new SkillTournamentCandidate(
                id,
                CanonicalObjectHasher.sha256(id),
                SkillEvolutionAuthoredCandidate.from(Map.of(
                        "patchType", "PATCH",
                        "changes", List.of(Map.of(
                                "operation", "REPLACE",
                                "path", "/procedure",
                                "value", id)),
                        "authoringSource", "TEST")),
                complexity);
    }

    private SkillStructuralVerification structural(String candidateId, boolean passed) {
        return new SkillStructuralVerification(
                candidateId, passed, passed ? 0.9 : 0.2,
                passed ? List.of() : List.of("STRUCTURAL_INVALID"),
                VERSION.structuralVersion());
    }

    private SkillHiddenEvaluationSet hidden(String candidateId) {
        return new SkillHiddenEvaluationSet(
                "suite-" + candidateId,
                List.of(Map.of("caseId", "hidden-1", "input", "secret")),
                List.of(Map.of("caseId", "mutation-1", "mutation", "negation")),
                CanonicalObjectHasher.sha256(candidateId + ":hidden"),
                CanonicalObjectHasher.sha256(candidateId + ":mutation"));
    }

    private SkillBehaviorEvaluation behavior(
            SkillTournamentCandidate candidate,
            boolean admitted) {
        SkillBehaviorReplayResult noSkill = replay(
                SkillBehaviorReplayArm.NO_SKILL, "", 0.2, 0.4, 0.4, 500);
        SkillBehaviorReplayResult baseline = replay(
                SkillBehaviorReplayArm.BASELINE, "baseline-hash", 0.6, 0.7, 0.7, 900);
        SkillBehaviorReplayResult next = replay(
                SkillBehaviorReplayArm.CANDIDATE, candidate.candidateHash(),
                admitted ? 0.8 : 0.5,
                admitted ? 0.85 : 0.6,
                admitted ? 0.85 : 0.6,
                1_000 + candidate.patchComplexity());
        return new SkillBehaviorEvaluation(
                "evaluation-" + candidate.candidateId(), noSkill, baseline, next,
                admitted, admitted ? List.of() : List.of("BEHAVIOR_REGRESSION"),
                VERSION.behaviorVersion());
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArm arm,
            String activeHash,
            double success,
            double evidence,
            double routing,
            long cost) {
        return new SkillBehaviorReplayResult(
                arm, activeHash,
                new SkillBehaviorMetrics(
                        success, 0, 1, 0, evidence, 0.02,
                        100, 200, cost, evidence, routing),
                CanonicalObjectHasher.sha256(arm.name() + ":answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence"),
                List.of(), List.of());
    }

    private SkillModelJudgeEvaluation judge(
            String candidateId,
            SkillModelJudgeDisposition disposition,
            double score) {
        return new SkillModelJudgeEvaluation(
                candidateId, disposition, score,
                disposition == SkillModelJudgeDisposition.PASS
                        ? List.of() : List.of("JUDGE_REVIEW"),
                VERSION.judgeVersion());
    }
}
