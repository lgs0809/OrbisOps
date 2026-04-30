package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillBehaviorEvaluation;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;

import java.util.ArrayList;
import java.util.List;

/** Deterministic candidate admission. Model judges cannot override these gates. */
public final class SkillBehaviorAdmissionPolicy {

    public SkillBehaviorEvaluation evaluate(
            String evaluationId,
            SkillBehaviorReplayResult noSkill,
            SkillBehaviorReplayResult baseline,
            SkillBehaviorReplayResult candidate,
            double minimumSuccessDelta,
            long maximumCostMicros,
            String verifierVersion) {
        if (!Double.isFinite(minimumSuccessDelta) || minimumSuccessDelta < 0D || minimumSuccessDelta > 1D) {
            throw new IllegalArgumentException("SKILL_REPLAY_SUCCESS_DELTA_INVALID");
        }
        if (maximumCostMicros < 0) throw new IllegalArgumentException("SKILL_REPLAY_COST_BUDGET_INVALID");
        List<String> reasons = new ArrayList<>();
        SkillBehaviorMetrics base = baseline.metrics();
        SkillBehaviorMetrics next = candidate.metrics();
        if (!next.safe() || next.safetyViolationCount() > base.safetyViolationCount()) {
            reasons.add("SKILL_REPLAY_SAFETY_REGRESSION");
        }
        if (next.successRate() + 1e-9D < base.successRate() + minimumSuccessDelta) {
            reasons.add("SKILL_REPLAY_SUCCESS_DELTA_NOT_MET");
        }
        if (next.routingAccuracy() + 1e-9D < base.routingAccuracy()) {
            reasons.add("SKILL_REPLAY_ROUTING_REGRESSION");
        }
        if (next.evidenceCompleteness() + 1e-9D < base.evidenceCompleteness()) {
            reasons.add("SKILL_REPLAY_EVIDENCE_REGRESSION");
        }
        if (next.hallucinationRate() > base.hallucinationRate() + 1e-9D) {
            reasons.add("SKILL_REPLAY_HALLUCINATION_REGRESSION");
        }
        if (next.invalidToolCallCount() > base.invalidToolCallCount()) {
            reasons.add("SKILL_REPLAY_INVALID_TOOL_REGRESSION");
        }
        if (next.finalAnswerQuality() + 1e-9D < base.finalAnswerQuality()) {
            reasons.add("SKILL_REPLAY_ANSWER_QUALITY_REGRESSION");
        }
        if (next.costMicros() > maximumCostMicros) {
            reasons.add("SKILL_REPLAY_COST_BUDGET_EXCEEDED");
        }
        return new SkillBehaviorEvaluation(
                evaluationId, noSkill, baseline, candidate,
                reasons.isEmpty(), reasons, verifierVersion);
    }
}
