package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Instance-free task shape used to cluster repeated Skill observations. */
public record SkillExperienceTaskTemplate(
        String intent,
        String problemPattern,
        String triggerType,
        List<String> evidenceTypes,
        String outcome) {

    public SkillExperienceTaskTemplate {
        evidenceTypes = evidenceTypes == null
                ? List.of()
                : List.copyOf(evidenceTypes);
    }
}
