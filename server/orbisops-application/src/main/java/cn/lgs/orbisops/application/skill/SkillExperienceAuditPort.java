package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;

/** Audit output port for newly admitted Skill observations. */
public interface SkillExperienceAuditPort {

    void recordObservation(SkillExperienceObservation observation);
}
