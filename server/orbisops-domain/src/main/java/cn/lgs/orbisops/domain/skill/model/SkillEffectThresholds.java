package cn.lgs.orbisops.domain.skill.model;

/** Governance thresholds for one Skill effect comparison. */
public record SkillEffectThresholds(
        int minimumSampleSize,
        double maximumBlockedToolRate,
        double maximumNeedsReplanRate,
        double maximumNegativeFeedbackRate,
        double maximumSuccessRateDrop,
        double maximumEvidenceRateDrop,
        double maximumToolCallIncreaseRate) {

    public SkillEffectThresholds {
        minimumSampleSize = Math.max(1, minimumSampleSize);
        maximumBlockedToolRate = bounded(maximumBlockedToolRate);
        maximumNeedsReplanRate = bounded(maximumNeedsReplanRate);
        maximumNegativeFeedbackRate = bounded(maximumNegativeFeedbackRate);
        maximumSuccessRateDrop = bounded(maximumSuccessRateDrop);
        maximumEvidenceRateDrop = bounded(maximumEvidenceRateDrop);
        maximumToolCallIncreaseRate = Math.max(0D, maximumToolCallIncreaseRate);
    }

    private static double bounded(double value) {
        return Math.max(0D, Math.min(value, 1D));
    }
}
