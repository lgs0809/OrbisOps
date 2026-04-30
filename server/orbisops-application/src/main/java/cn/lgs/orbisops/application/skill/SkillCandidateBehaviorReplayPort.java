package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

@FunctionalInterface
public interface SkillCandidateBehaviorReplayPort {
    SkillBehaviorEvaluation verify(
            SkillTournamentCandidate candidate,
            SkillHiddenEvaluationSet evaluationSet,
            SkillVerifierVersion verifierVersion);

    default SkillBehaviorEvaluation verify(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            SkillHiddenEvaluationSet evaluationSet) {
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        return verify(candidate, evaluationSet, context.verifierVersion());
    }
}
