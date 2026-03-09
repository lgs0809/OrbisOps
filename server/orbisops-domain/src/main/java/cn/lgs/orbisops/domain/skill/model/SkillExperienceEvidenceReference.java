package cn.lgs.orbisops.domain.skill.model;

/** Trusted evidence reference retained in a reusable Skill observation. */
public record SkillExperienceEvidenceReference(
        String evidenceId,
        String resultId,
        String outputHash,
        String sourceType) {
}
