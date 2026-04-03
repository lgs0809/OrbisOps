package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillModelJudgeEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

@FunctionalInterface
public interface SkillModelJudgePort {
    SkillModelJudgeEvaluation judge(
            SkillTournamentCandidate candidate,
            SkillBehaviorEvaluation behavior,
            SkillHiddenEvaluationSet evaluationSet,
            SkillVerifierVersion verifierVersion);

    default SkillModelJudgeEvaluation judge(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate,
            SkillBehaviorEvaluation behavior,
            SkillHiddenEvaluationSet evaluationSet) {
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        return judge(candidate, behavior, evaluationSet, context.verifierVersion());
    }
}
