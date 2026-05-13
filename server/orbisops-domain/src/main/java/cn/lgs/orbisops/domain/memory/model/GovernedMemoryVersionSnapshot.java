package cn.lgs.orbisops.domain.memory.model;

/** Append-only governed-memory version payload. */
public record GovernedMemoryVersionSnapshot(
        String memoryId,
        int version,
        String memoryHash,
        String status,
        String content,
        String normalizedContent,
        String sourceRunId,
        String createdBy) {

    public GovernedMemoryVersionSnapshot {
        memoryId = value(memoryId);
        memoryHash = value(memoryHash);
        status = value(status);
        content = value(content);
        normalizedContent = value(normalizedContent);
        sourceRunId = value(sourceRunId);
        createdBy = value(createdBy);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
