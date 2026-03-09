package cn.lgs.orbisops.trigger.ops.skill;

/** Typed acceptance threshold for Skill Evolution similarity matching. */
public record OpsSkillSimilaritySettings(double threshold) {

    public OpsSkillSimilaritySettings {
        threshold = Double.isFinite(threshold)
                && threshold >= 0D
                && threshold <= 1D
                ? threshold
                : 0.72D;
    }

    public static OpsSkillSimilaritySettings defaults() {
        return new OpsSkillSimilaritySettings(0.72D);
    }

    static OpsSkillSimilaritySettings legacyConstructorDefaults() {
        return new OpsSkillSimilaritySettings(0D);
    }
}
