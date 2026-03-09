package cn.lgs.orbisops.domain.skill.model;

/** Aggregate counters for one repeated Skill experience cluster. */
public record SkillExperienceClusterSnapshot(
        int observationCount,
        int successfulCount,
        int evidenceCount,
        String status,
        int version) {
}
