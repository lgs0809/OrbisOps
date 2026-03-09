package cn.lgs.orbisops.application.rag;

/** One retrieval hit paired with its domain quality score and stable rank. */
public record RagQualityScoredHit<M>(
        int rank,
        RagQualityRetrievalHit<M> hit,
        double score) {

    public RagQualityScoredHit {
        if (rank <= 0) throw new IllegalArgumentException("RAG_QUALITY_HIT_RANK_INVALID");
        if (hit == null) throw new IllegalArgumentException("RAG_QUALITY_HIT_REQUIRED");
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0D) {
            throw new IllegalArgumentException("RAG_QUALITY_HIT_SCORE_INVALID");
        }
    }
}
