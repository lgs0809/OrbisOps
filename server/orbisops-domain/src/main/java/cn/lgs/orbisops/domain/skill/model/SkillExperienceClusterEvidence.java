package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

/** Source diversity and hard-case composition for one experience cluster. */
public record SkillExperienceClusterEvidence(
        int distinctRuns,
        int distinctSessions,
        int hardCaseCount,
        int successfulCount,
        List<String> observationTypes,
        int distinctConditions) {

    public SkillExperienceClusterEvidence(int distinctRuns,int distinctSessions,int hardCaseCount,int successfulCount,List<String> observationTypes) {
        this(distinctRuns,distinctSessions,hardCaseCount,successfulCount,observationTypes,0);
    }

    public SkillExperienceClusterEvidence {
        observationTypes = observationTypes == null
                ? List.of()
                : List.copyOf(observationTypes);
    }

    public static SkillExperienceClusterEvidence empty() {
        return new SkillExperienceClusterEvidence(0, 0, 0, 0, List.of());
    }
}
