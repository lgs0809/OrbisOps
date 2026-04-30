package cn.lgs.orbisops.domain.skill.model;

public record SkillBehaviorMetrics(
        double successRate,
        int safetyViolationCount,
        int toolCallCount,
        int invalidToolCallCount,
        double evidenceCompleteness,
        double hallucinationRate,
        long latencyMs,
        long tokenCount,
        long costMicros,
        double finalAnswerQuality,
        double routingAccuracy
) {

    public SkillBehaviorMetrics {
        successRate = rate(successRate, "SKILL_REPLAY_SUCCESS_RATE_INVALID");
        if (safetyViolationCount < 0 || toolCallCount < 0 || invalidToolCallCount < 0) {
            throw new IllegalArgumentException("SKILL_REPLAY_TOOL_METRIC_INVALID");
        }
        if (invalidToolCallCount > toolCallCount) {
            throw new IllegalArgumentException("SKILL_REPLAY_INVALID_TOOL_COUNT_EXCEEDS_TOTAL");
        }
        evidenceCompleteness = rate(evidenceCompleteness, "SKILL_REPLAY_EVIDENCE_RATE_INVALID");
        hallucinationRate = rate(hallucinationRate, "SKILL_REPLAY_HALLUCINATION_RATE_INVALID");
        if (latencyMs < 0 || tokenCount < 0 || costMicros < 0) {
            throw new IllegalArgumentException("SKILL_REPLAY_COST_METRIC_INVALID");
        }
        finalAnswerQuality = rate(finalAnswerQuality, "SKILL_REPLAY_ANSWER_QUALITY_INVALID");
        routingAccuracy = rate(routingAccuracy, "SKILL_REPLAY_ROUTING_ACCURACY_INVALID");
    }

    public boolean safe() {
        return safetyViolationCount == 0;
    }

    private static double rate(double value, String reasonCode) {
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(reasonCode);
        }
        return value;
    }
}
