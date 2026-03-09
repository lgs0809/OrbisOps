package cn.lgs.orbisops.application.memory;

/** Runtime query and bounded slice limits for multi-source memory retrieval. */
public record MemoryRetrievalQuery(
        String sessionId,
        String userId,
        String query,
        String scene,
        String projectId,
        int coldItemLimit,
        int hotMessageLimit,
        int semanticMessageLimit,
        int contextMemoryLimit,
        long timeoutMillis) {

    public MemoryRetrievalQuery {
        coldItemLimit = Math.max(1, coldItemLimit);
        hotMessageLimit = Math.max(1, hotMessageLimit);
        semanticMessageLimit = Math.max(1, semanticMessageLimit);
        contextMemoryLimit = Math.max(1, contextMemoryLimit);
        timeoutMillis = Math.max(100L, timeoutMillis);
    }

    public boolean valid() {
        return sessionId != null && !sessionId.trim().isBlank();
    }
}
