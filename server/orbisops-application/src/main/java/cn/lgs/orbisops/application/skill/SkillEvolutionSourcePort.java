package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInput;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;

/** Loads an immutable, accepted task episode for the currently owned job attempt. */
public interface SkillEvolutionSourcePort {
    SkillEvolutionInput load(SkillEvolutionJobSnapshot job);
}
