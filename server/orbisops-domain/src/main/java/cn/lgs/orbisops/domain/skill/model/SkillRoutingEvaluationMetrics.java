package cn.lgs.orbisops.domain.skill.model;

public record SkillRoutingEvaluationMetrics(
        int caseCount,
        int positiveCount,
        int negativeCount,
        int top1CorrectCount,
        int top3CorrectCount,
        int falsePositiveCount,
        int falseNegativeCount,
        int shadowingCount,
        int noSkillTruePositiveCount,
        int noSkillSelectedCount,
        double averageMargin,
        double top1Accuracy,
        double top3Accuracy,
        double falsePositiveRate,
        double falseNegativeRate,
        double noSkillPrecision
) {

    public SkillRoutingEvaluationMetrics {
        if (caseCount < 0 || positiveCount < 0 || negativeCount < 0
                || top1CorrectCount < 0 || top3CorrectCount < 0
                || falsePositiveCount < 0 || falseNegativeCount < 0
                || shadowingCount < 0 || noSkillTruePositiveCount < 0
                || noSkillSelectedCount < 0) {
            throw new IllegalArgumentException("SKILL_ROUTING_METRIC_COUNT_INVALID");
        }
        averageMargin = rate(averageMargin, "SKILL_ROUTING_MARGIN_INVALID");
        top1Accuracy = rate(top1Accuracy, "SKILL_ROUTING_TOP1_ACCURACY_INVALID");
        top3Accuracy = rate(top3Accuracy, "SKILL_ROUTING_TOP3_ACCURACY_INVALID");
        falsePositiveRate = rate(falsePositiveRate, "SKILL_ROUTING_FP_RATE_INVALID");
        falseNegativeRate = rate(falseNegativeRate, "SKILL_ROUTING_FN_RATE_INVALID");
        noSkillPrecision = rate(noSkillPrecision, "SKILL_ROUTING_NO_SKILL_PRECISION_INVALID");
    }

    private static double rate(double value, String reasonCode) {
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(reasonCode);
        }
        return value;
    }
}
