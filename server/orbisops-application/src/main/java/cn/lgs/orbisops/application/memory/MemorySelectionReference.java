package cn.lgs.orbisops.application.memory;

/** Typed authoritative reference for one selected Context Memory entry. */
public record MemorySelectionReference(
        String memoryId,
        int version,
        String memoryHash,
        String memoryType,
        String scope,
        String scopeId,
        String sourceMessageHash,
        String contentHash,
        String selectedAt,
        boolean verified) {

    public MemorySelectionReference {
        memoryId = value(memoryId);
        memoryHash = value(memoryHash);
        memoryType = value(memoryType);
        scope = value(scope);
        scopeId = value(scopeId);
        sourceMessageHash = value(sourceMessageHash);
        contentHash = value(contentHash);
        selectedAt = value(selectedAt);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
