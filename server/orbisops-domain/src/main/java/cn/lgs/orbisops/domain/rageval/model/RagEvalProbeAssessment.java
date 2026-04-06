package cn.lgs.orbisops.domain.rageval.model;

import java.util.List;

/** Immutable quality assessment for one RAG retrieval probe. */
public record RagEvalProbeAssessment(
        int hitCount,
        List<Double> hitScores,
        List<String> coveredKeywords,
        List<String> missingKeywords,
        double keywordCoverage,
        double reciprocalRank,
        boolean passed,
        String recommendation) {

    public RagEvalProbeAssessment {
        if (hitCount < 0) throw new IllegalArgumentException("RAG_EVAL_HIT_COUNT_INVALID");
        hitScores = hitScores == null ? List.of() : List.copyOf(hitScores);
        coveredKeywords = coveredKeywords == null ? List.of() : List.copyOf(coveredKeywords);
        missingKeywords = missingKeywords == null ? List.of() : List.copyOf(missingKeywords);
        keywordCoverage = bounded(keywordCoverage);
        reciprocalRank = bounded(reciprocalRank);
        recommendation = recommendation == null ? "" : recommendation.trim();
        if (hitScores.size() != hitCount) {
            throw new IllegalArgumentException("RAG_EVAL_HIT_SCORE_CARDINALITY_MISMATCH");
        }
    }

    private static double bounded(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0D;
        return Math.max(0D, Math.min(1D, value));
    }
}
