package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayArm;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionCandidateSetSelectorTest {

    private static final SkillVerifierVersion VERSION =
            new SkillVerifierVersion("struct-v1", "behavior-v1", "judge-v1");

    @Test
    void legacyModeMustRemainExplicitAndSelectFirstReusableByCandidateIndex() {
        SkillEvolutionCandidateSetSelector selector =
                new SkillEvolutionCandidateSetSelector();
        List<SkillEvolutionAuthoredCandidateOption> options = List.of(
                option(2, false), option(1, true), option(0, false));

        SkillCandidateSetSelectionResult result = selector.select(
                SkillCandidateSelectionMode.LEGACY_COMPATIBILITY,
                options, "unused", "", 1, null);

        assertEquals(SkillCandidateSelectionMode.LEGACY_COMPATIBILITY, result.mode());
        assertTrue(result.selected());
        assertEquals("candidate-1", result.selectedCandidate().payload().get("name"));
        assertEquals("SKILL_LEGACY_FIRST_REUSABLE_SELECTED", result.reasonCode());
    }

    @Test
    void strictModeMustFailWhenTournamentIsNotConfigured() {
        SkillEvolutionCandidateSetSelector selector =
                new SkillEvolutionCandidateSetSelector();

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                selector.select(
                        SkillCandidateSelectionMode.STRICT_TOURNAMENT,
                        List.of(option(0, true), option(1, true)),
                        "tournament-1", "skill-1", 7, VERSION));

        assertEquals("SKILL_STRICT_TOURNAMENT_NOT_CONFIGURED", error.getMessage());
    }

    @Test
    void strictModeMustSelectOnlyTournamentWinner() {
        SkillCandidateTournament tournament = new SkillCandidateTournament(
                (candidate, version) -> new SkillStructuralVerification(
                        candidate.candidateId(), true, 0.9, List.of(),
                        version.structuralVersion()),
                (skillId, baseVersion, candidate) -> new SkillHiddenEvaluationSet(
                        "suite-" + candidate.candidateId(),
                        List.of(Map.of("caseId", "hidden-1")),
                        List.of(Map.of("caseId", "mutation-1")),
                        CanonicalObjectHasher.sha256(candidate.candidateId() + ":hidden"),
                        CanonicalObjectHasher.sha256(candidate.candidateId() + ":mutation")),
                (candidate, evaluationSet, version) -> behavior(
                        candidate.candidateHash(),
                        candidate.candidateId().equals("candidate-1") ? 0.9 : 0.8,
                        version.behaviorVersion()),
                (candidate, behavior, evaluationSet, version) ->
                        new SkillModelJudgeEvaluation(
                                candidate.candidateId(), SkillModelJudgeDisposition.PASS,
                                0.8, List.of(), version.judgeVersion()));
        SkillEvolutionCandidateSetSelector selector =
                new SkillEvolutionCandidateSetSelector(tournament);

        SkillCandidateSetSelectionResult result = selector.select(
                SkillCandidateSelectionMode.STRICT_TOURNAMENT,
                List.of(option(0, true), option(1, true)),
                "tournament-1", "skill-1", 7, VERSION);

        assertEquals(SkillCandidateSelectionMode.STRICT_TOURNAMENT, result.mode());
        assertEquals("candidate-1", result.tournamentResult().selectedCandidateId());
        assertEquals("candidate-1", result.selectedCandidate().payload().get("name"));
        assertEquals("SKILL_STRICT_TOURNAMENT_SELECTED", result.reasonCode());
    }

    private SkillEvolutionAuthoredCandidateOption option(
            int index,
            boolean reusable) {
        Map<String, Object> payload = reusable
                ? Map.of(
                        "name", "candidate-" + index,
                        "patchType", "PATCH",
                        "changes", List.of(Map.of(
                                "operation", "REPLACE",
                                "path", "/procedure",
                                "value", "candidate-" + index)),
                        "authoringSource", "TEST")
                : Map.of(
                        "name", "candidate-" + index,
                        "patchType", "NO_CHANGE",
                        "authoringSource", "TEST");
        return new SkillEvolutionAuthoredCandidateOption(
                SkillEvolutionAuthoredCandidate.from(payload),
                new SkillEvolutionAuthoringAudit(
                        "model-1", "prompt-v1", 42L,
                        "input-hash", 3, index,
                        SkillAuthoringDirection.values()[index]));
    }

    private SkillBehaviorEvaluation behavior(
            String candidateHash,
            double success,
            String verifierVersion) {
        SkillBehaviorReplayResult noSkill = replay(
                SkillBehaviorReplayArm.NO_SKILL, "", 0.2);
        SkillBehaviorReplayResult baseline = replay(
                SkillBehaviorReplayArm.BASELINE, "baseline-hash", 0.6);
        SkillBehaviorReplayResult candidate = replay(
                SkillBehaviorReplayArm.CANDIDATE, candidateHash, success);
        return new SkillBehaviorEvaluation(
                "evaluation-" + candidateHash, noSkill, baseline, candidate,
                true, List.of(), verifierVersion);
    }

    private SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArm arm,
            String activeHash,
            double success) {
        return new SkillBehaviorReplayResult(
                arm, activeHash,
                new SkillBehaviorMetrics(
                        success, 0, 1, 0, 0.8, 0.01,
                        100, 200, 500, 0.8, 0.8),
                CanonicalObjectHasher.sha256(arm.name() + ":answer"),
                CanonicalObjectHasher.sha256(arm.name() + ":evidence"),
                List.of(), List.of());
    }
}
