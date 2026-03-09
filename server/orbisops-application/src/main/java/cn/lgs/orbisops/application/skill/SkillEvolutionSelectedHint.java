package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;

/** Relevant authoring hint selected for one Skill Evolution decision. */
public record SkillEvolutionSelectedHint(
        SkillEvolutionHintSnapshot hint,
        String content) {

    public SkillEvolutionSelectedHint {
        if (hint == null) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_HINT_REQUIRED");
        }
        content = content == null ? "" : content.trim();
    }
}
