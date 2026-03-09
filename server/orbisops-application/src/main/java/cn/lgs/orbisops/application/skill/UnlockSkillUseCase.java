package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;

public final class UnlockSkillUseCase {
    private final SkillGovernanceTransitionUseCase transitions;

    public UnlockSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        if (transitions == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_TRANSITIONS_REQUIRED");
        this.transitions = transitions;
    }

    public SkillCatalogEntry execute(SkillGovernanceCommand command) {
        return transitions.unlock(command);
    }
}
