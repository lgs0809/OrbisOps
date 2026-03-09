package cn.lgs.orbisops.application.memory;

/** Typed runtime request for retrieving, selecting, rendering and referencing memory. */
public record MemoryQueryCommand(
        String sessionId,
        String userId,
        String query,
        String explicitScene,
        String taskType,
        String projectId,
        int itemMatchLimit,
        int hotMessageLimit,
        int semanticTopK,
        int contextMaxChars,
        long timeoutMillis,
        boolean recencyAware,
        double recencyHalfLifeTurns) {

    public MemoryQueryCommand {
        itemMatchLimit = Math.max(1, itemMatchLimit);
        hotMessageLimit = Math.max(1, hotMessageLimit);
        semanticTopK = Math.max(1, semanticTopK);
        contextMaxChars = Math.max(1200, contextMaxChars);
        timeoutMillis = Math.max(100L, timeoutMillis);
        recencyHalfLifeTurns = Math.max(1D, recencyHalfLifeTurns);
    }

    public boolean valid() {
        return sessionId != null && !sessionId.trim().isBlank();
    }
}
