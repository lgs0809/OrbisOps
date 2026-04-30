package cn.lgs.orbisops.application.skill;

@FunctionalInterface
public interface SkillHiddenEvaluationSetPort {
    SkillHiddenEvaluationSet load(
            String skillId,
            long baseVersion,
            SkillTournamentCandidate candidate);

    default SkillHiddenEvaluationSet load(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate) {
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        return load(context.skillId(), context.baseVersion(), candidate);
    }
}
