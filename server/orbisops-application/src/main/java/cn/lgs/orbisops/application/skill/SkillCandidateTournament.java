package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateTournamentResult;
import cn.lgs.orbisops.domain.skill.model.SkillCandidateVerification;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeDisposition;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCandidateSelectionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Strict Structural -> Behavior -> Judge tournament. */
public final class SkillCandidateTournament {

    private final SkillStructuralVerifierPort structuralVerifier;
    private final SkillHiddenEvaluationSetPort hiddenEvaluationSetPort;
    private final SkillCandidateBehaviorReplayPort behaviorReplay;
    private final SkillModelJudgePort modelJudge;
    private final SkillCandidateSelectionPolicy selectionPolicy;

    public SkillCandidateTournament(
            SkillStructuralVerifierPort structuralVerifier,
            SkillHiddenEvaluationSetPort hiddenEvaluationSetPort,
            SkillCandidateBehaviorReplayPort behaviorReplay,
            SkillModelJudgePort modelJudge) {
        this(structuralVerifier, hiddenEvaluationSetPort, behaviorReplay,
                modelJudge, new SkillCandidateSelectionPolicy());
    }

    SkillCandidateTournament(
            SkillStructuralVerifierPort structuralVerifier,
            SkillHiddenEvaluationSetPort hiddenEvaluationSetPort,
            SkillCandidateBehaviorReplayPort behaviorReplay,
            SkillModelJudgePort modelJudge,
            SkillCandidateSelectionPolicy selectionPolicy) {
        if (structuralVerifier == null || hiddenEvaluationSetPort == null
                || behaviorReplay == null || modelJudge == null || selectionPolicy == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_DEPENDENCY_REQUIRED");
        }
        this.structuralVerifier = structuralVerifier;
        this.hiddenEvaluationSetPort = hiddenEvaluationSetPort;
        this.behaviorReplay = behaviorReplay;
        this.modelJudge = modelJudge;
        this.selectionPolicy = selectionPolicy;
    }

    public SkillCandidateTournamentResult run(
            String tournamentId,
            String skillId,
            long baseVersion,
            List<SkillTournamentCandidate> candidates,
            SkillVerifierVersion verifierVersion) {
        String effectiveSkillId = required(skillId, "SKILL_TOURNAMENT_SKILL_ID_REQUIRED");
        return run(new SkillCandidateTournamentContext(
                tournamentId,
                "legacy",
                effectiveSkillId,
                baseVersion,
                CanonicalObjectHasher.sha256(Map.of(
                        "skillId", effectiveSkillId,
                        "baseVersion", baseVersion)),
                "legacy",
                verifierVersion), candidates);
    }

    public SkillCandidateTournamentResult run(
            SkillCandidateTournamentContext context,
            List<SkillTournamentCandidate> candidates) {
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        List<SkillTournamentCandidate> ordered = candidates == null ? List.of()
                : candidates.stream()
                .sorted(Comparator.comparing(SkillTournamentCandidate::candidateId))
                .toList();
        if (ordered.size() < 2 || ordered.size() > 4) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CANDIDATE_COUNT_INVALID");
        }
        if (ordered.stream().map(SkillTournamentCandidate::candidateId).distinct().count()
                != ordered.size()) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CANDIDATE_DUPLICATE");
        }

        List<SkillCandidateVerification> verifications = new ArrayList<>();
        for (SkillTournamentCandidate candidate : ordered) {
            SkillStructuralVerification structural =
                    structuralVerifier.verify(context, candidate);
            assertStructural(candidate, structural, context.verifierVersion());
            if (!structural.passed()) {
                verifications.add(new SkillCandidateVerification(
                        candidate.candidateId(), candidate.candidateHash(), structural,
                        null, null, "", "", candidate.patchComplexity()));
                continue;
            }

            SkillHiddenEvaluationSet evaluationSet = hiddenEvaluationSetPort.load(
                    context, candidate);
            if (evaluationSet == null) {
                throw new IllegalStateException(
                        "SKILL_HIDDEN_EVAL_SET_MISSING:" + candidate.candidateId());
            }
            SkillBehaviorEvaluation behavior = behaviorReplay.verify(
                    context, candidate, evaluationSet);
            assertBehavior(candidate, behavior, context.verifierVersion());
            if (!behavior.admitted()) {
                verifications.add(new SkillCandidateVerification(
                        candidate.candidateId(), candidate.candidateHash(), structural,
                        behavior, null, evaluationSet.hiddenEvalHash(),
                        evaluationSet.mutationEvalHash(), candidate.patchComplexity()));
                continue;
            }

            SkillModelJudgeEvaluation judge = judge(
                    context, candidate, behavior, evaluationSet);
            verifications.add(new SkillCandidateVerification(
                    candidate.candidateId(), candidate.candidateHash(), structural,
                    behavior, judge, evaluationSet.hiddenEvalHash(),
                    evaluationSet.mutationEvalHash(), candidate.patchComplexity()));
        }
        return selectionPolicy.select(
                context.tournamentId(), verifications, context.verifierVersion());
    }

    private SkillModelJudgeEvaluation judge(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            SkillBehaviorEvaluation behavior,
            SkillHiddenEvaluationSet evaluationSet) {
        try {
            SkillModelJudgeEvaluation result = modelJudge.judge(
                    context, candidate, behavior, evaluationSet);
            if (result == null) {
                return unavailableJudge(
                        candidate, context.verifierVersion(), "SKILL_JUDGE_RESULT_MISSING");
            }
            if (!candidate.candidateId().equals(result.candidateId())) {
                throw new IllegalStateException("SKILL_JUDGE_CANDIDATE_MISMATCH");
            }
            if (!context.verifierVersion().judgeVersion().equals(result.judgeVersion())) {
                throw new IllegalStateException("SKILL_JUDGE_VERSION_MISMATCH");
            }
            return result;
        } catch (RuntimeException error) {
            return unavailableJudge(candidate, context.verifierVersion(),
                    "SKILL_JUDGE_UNAVAILABLE:" + error.getClass().getSimpleName());
        }
    }

    private SkillModelJudgeEvaluation unavailableJudge(
            SkillTournamentCandidate candidate,
            SkillVerifierVersion verifierVersion,
            String reasonCode) {
        return new SkillModelJudgeEvaluation(
                candidate.candidateId(),
                SkillModelJudgeDisposition.UNAVAILABLE,
                0D,
                List.of(reasonCode),
                verifierVersion.judgeVersion());
    }

    private void assertStructural(
            SkillTournamentCandidate candidate,
            SkillStructuralVerification structural,
            SkillVerifierVersion verifierVersion) {
        if (structural == null) {
            throw new IllegalStateException(
                    "SKILL_STRUCTURAL_RESULT_MISSING:" + candidate.candidateId());
        }
        if (!candidate.candidateId().equals(structural.candidateId())) {
            throw new IllegalStateException("SKILL_STRUCTURAL_CANDIDATE_MISMATCH");
        }
        if (!verifierVersion.structuralVersion().equals(structural.verifierVersion())) {
            throw new IllegalStateException("SKILL_STRUCTURAL_VERSION_MISMATCH");
        }
    }

    private void assertBehavior(
            SkillTournamentCandidate candidate,
            SkillBehaviorEvaluation behavior,
            SkillVerifierVersion verifierVersion) {
        if (behavior == null) {
            throw new IllegalStateException(
                    "SKILL_BEHAVIOR_RESULT_MISSING:" + candidate.candidateId());
        }
        if (!verifierVersion.behaviorVersion().equals(behavior.verifierVersion())) {
            throw new IllegalStateException("SKILL_BEHAVIOR_VERSION_MISMATCH");
        }
        String activeHash = behavior.candidate().activeSkillHash();
        if (!candidate.candidateId().equals(activeHash)
                && !candidate.candidateHash().equals(activeHash)) {
            throw new IllegalStateException("SKILL_BEHAVIOR_CANDIDATE_MISMATCH");
        }
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
