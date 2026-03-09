package cn.lgs.orbisops.application.memory;

/** Typed semantic-memory retrieval request. */
public record SemanticMemoryRetrievalQuery(
        String sessionId,
        String userId,
        String query,
        int limit,
        int semanticTopK,
        boolean embeddingAvailable,
        boolean recencyAware,
        double recencyHalfLifeTurns) {

    public SemanticMemoryRetrievalQuery {
        sessionId = value(sessionId);
        userId = value(userId);
        query = value(query);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
