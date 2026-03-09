package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEffectMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillEffectThresholds;

/** Evaluates absolute safety limits and candidate regression against a baseline. */
public class SkillEffectDegradationPolicy {

    public boolean enoughSamples(
            SkillEffectMetrics metrics,
            SkillEffectThresholds thresholds) {
        return metrics != null
                && metrics.usedRunCount() >= thresholds.minimumSampleSize();
    }

    public String degradationReason(
            SkillEffectMetrics candidate,
            SkillEffectMetrics baseline,
            SkillEffectThresholds thresholds) {
        SkillEffectMetrics current = candidate == null
                ? SkillEffectMetrics.from(null)
                : candidate;
        SkillEffectMetrics previous = baseline == null
                ? SkillEffectMetrics.from(null)
                : baseline;
        double used = Math.max(1D, current.usedRunCount());
        if (current.blockedToolCallCount() / used
                > thresholds.maximumBlockedToolRate()) {
            return "BLOCKED_TOOL_RATE_DEGRADED";
        }
        if (current.needsReplanCount() / used
                > thresholds.maximumNeedsReplanRate()) {
            return "NEEDS_REPLAN_RATE_DEGRADED";
        }
        if (current.userNegativeFeedbackCount() / used
                > thresholds.maximumNegativeFeedbackRate()) {
            return "NEGATIVE_FEEDBACK_RATE_DEGRADED";
        }
        double baselineUsed = previous.usedRunCount();
        if (baselineUsed <= 0D) return "";

        double candidateSuccess = current.successfulRunCount() / used;
        double baselineSuccess = previous.successfulRunCount() / baselineUsed;
        if (baselineSuccess - candidateSuccess
                > thresholds.maximumSuccessRateDrop()) {
            return "SUCCESS_RATE_REGRESSION";
        }
        double candidateEvidence = current.evidenceSufficientCount() / used;
        double baselineEvidence = previous.evidenceSufficientCount() / baselineUsed;
        if (baselineEvidence - candidateEvidence
                > thresholds.maximumEvidenceRateDrop()) {
            return "EVIDENCE_RATE_REGRESSION";
        }
        double candidateToolCalls = current.toolCallCount() / used;
        double baselineToolCalls = previous.toolCallCount() / baselineUsed;
        if (baselineToolCalls > 0D
                && candidateToolCalls
                > baselineToolCalls
                * (1D + thresholds.maximumToolCallIncreaseRate())
                && candidateSuccess <= baselineSuccess) {
            return "TOOL_CALL_COST_REGRESSION";
        }
        return "";
    }
}
