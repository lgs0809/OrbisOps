package cn.lgs.orbisops.application.memory;

/** Typed raw filters for generic Context Memory search. */
public record ContextMemoryQuery(
        String scopeType,
        String scopeId,
        String memoryType,
        String status,
        int limit) {

    public ContextMemoryQuery {
        scopeType = value(scopeType);
        scopeId = value(scopeId);
        memoryType = value(memoryType);
        status = value(status);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
