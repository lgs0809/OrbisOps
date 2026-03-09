package cn.lgs.orbisops.domain.memory.model;

public record MemoryCandidate(MemoryType type,
                              MemoryScope scope,
                              String scopeId,
                              String content,
                              String normalizedContent,
                              String logicalKey,
                              boolean verified,
                              double confidence) {

    public MemoryCandidate {
        if (type == null) throw new IllegalArgumentException("MEMORY_TYPE_REQUIRED");
        scopeId = value(scopeId);
        content = value(content);
        normalizedContent = value(normalizedContent);
        logicalKey = value(logicalKey);
        if (type.persistable()) {
            if (scope == null) throw new IllegalArgumentException("MEMORY_SCOPE_REQUIRED");
            if (scopeId.isBlank()) throw new IllegalArgumentException("MEMORY_SCOPE_ID_REQUIRED");
            if (content.isBlank()) throw new IllegalArgumentException("MEMORY_CONTENT_REQUIRED");
            if (normalizedContent.isBlank()) throw new IllegalArgumentException("MEMORY_NORMALIZED_CONTENT_REQUIRED");
            if (logicalKey.isBlank()) throw new IllegalArgumentException("MEMORY_LOGICAL_KEY_REQUIRED");
        }
        if (confidence < 0.0D || confidence > 1.0D) {
            throw new IllegalArgumentException("MEMORY_CONFIDENCE_INVALID");
        }
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
