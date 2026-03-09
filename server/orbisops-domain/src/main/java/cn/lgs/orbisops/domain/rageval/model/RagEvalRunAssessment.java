package cn.lgs.orbisops.domain.rageval.model;

/** Aggregate quality metrics for one RAG evaluation run. */
public record RagEvalRunAssessment(
        int caseCount,
        double hitRate,
        double averageKeywordCoverage,
        double meanReciprocalRank,
        long passedCount) {

    public RagEvalRunAssessment {
        if (caseCount < 0) throw new IllegalArgumentException("RAG_EVAL_CASE_COUNT_INVALID");
        if (passedCount < 0 || passedCount > caseCount) {
            throw new IllegalArgumentException("RAG_EVAL_PASSED_COUNT_INVALID");
        }
        hitRate = bounded(hitRate);
        averageKeywordCoverage = bounded(averageKeywordCoverage);
        meanReciprocalRank = bounded(meanReciprocalRank);
    }

    private static double bounded(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0D;
        return Math.max(0D, Math.min(1D, value));
    }
}
