package cn.lgs.orbisops.application.skill;

/** One Skill version bound into a runtime context bundle. */
public record SkillRuntimeUsageReference(
        String skillId,
        int version,
        String skillHash,
        String usedAtNode) {
}
