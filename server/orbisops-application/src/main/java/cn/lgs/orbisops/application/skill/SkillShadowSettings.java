package cn.lgs.orbisops.application.skill;

/** Configuration for pre-release Skill shadow evaluation. */
public record SkillShadowSettings(boolean enabled, double minimumScore) {

    public SkillShadowSettings {
        minimumScore = Math.max(0D, Math.min(1D, minimumScore));
    }
}
