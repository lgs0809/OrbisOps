package cn.lgs.orbisops.domain.memory.model;

/** One semantic recall candidate with source-local rank and RRF contribution. */
public record SemanticMemoryRankedCandidate(
        SemanticMemoryDocumentSnapshot document,
        String source,
        int rank,
        double rrfScore) {

    public SemanticMemoryRankedCandidate {
        source = source == null ? "" : source.trim();
        rank = Math.max(1, rank);
    }
}
