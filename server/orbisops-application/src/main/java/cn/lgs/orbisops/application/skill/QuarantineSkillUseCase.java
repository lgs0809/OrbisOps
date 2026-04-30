package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;

public final class QuarantineSkillUseCase {
    private final SkillGovernanceTransitionUseCase transitions;

    public QuarantineSkillUseCase(SkillGovernanceTransitionUseCase transitions) {
        if (transitions == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_TRANSITIONS_REQUIRED");
        this.transitions = transitions;
    }

    public SkillCatalogEntry execute(SkillGovernanceCommand command) {
        return transitions.quarantine(command);
    }
}
