package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillStructuralVerification;
import cn.lgs.orbisops.domain.skill.model.SkillVerifierVersion;

@FunctionalInterface
public interface SkillStructuralVerifierPort {
    SkillStructuralVerification verify(
            SkillTournamentCandidate candidate,
            SkillVerifierVersion verifierVersion);

    default SkillStructuralVerification verify(
            SkillCandidateTournamentContext context,
            SkillTournamentCandidate candidate) {
        if (context == null) {
            throw new IllegalArgumentException("SKILL_TOURNAMENT_CONTEXT_REQUIRED");
        }
        return verify(candidate, context.verifierVersion());
    }
}
