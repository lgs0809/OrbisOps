package cn.lgs.orbisops.domain.memory.model;

/** Typed optional filters for persisted Context Memory searches. */
public record ContextMemorySearchCriteria(
        String scopeType,
        String scopeId,
        String memoryType,
        String status,
        int limit) {

    public ContextMemorySearchCriteria {
        scopeType = value(scopeType);
        scopeId = value(scopeId);
        memoryType = value(memoryType);
        status = value(status);
        limit = Math.max(1, Math.min(limit, 500));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
