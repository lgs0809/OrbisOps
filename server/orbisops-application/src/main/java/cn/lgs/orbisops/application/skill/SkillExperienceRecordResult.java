package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;

/** Outcome of admitting one runtime observation into a reusable experience cluster. */
public record SkillExperienceRecordResult(
        SkillExperienceObservation observation,
        SkillExperienceClusterSnapshot cluster,
        boolean newObservation) {
}
