package cn.lgs.orbisops.application.skill;

/** Runtime exposure configuration for Skill canary candidates. */
public record SkillCanarySettings(boolean enabled, int percent) {

    public SkillCanarySettings {
        percent = Math.max(0, Math.min(100, percent));
    }
}
