package cn.lgs.orbisops.application.skill;

/** Governance configuration for Skill release automation. */
public record SkillReleaseSettings(
        boolean enabled,
        int minimumSampleSize,
        int evaluationBatchSize) {

    public SkillReleaseSettings {
        minimumSampleSize = Math.max(1, minimumSampleSize);
        evaluationBatchSize = Math.max(1, Math.min(100, evaluationBatchSize));
    }
}
