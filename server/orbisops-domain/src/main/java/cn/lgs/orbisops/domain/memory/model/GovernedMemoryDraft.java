package cn.lgs.orbisops.domain.memory.model;

/** Typed normalized draft for governed explicit-memory persistence. */
public record GovernedMemoryDraft(
        MemoryScope scope,
        String scopeId,
        MemoryType type,
        String content,
        String normalizedContent,
        String logicalKey,
        String sourceType,
        boolean verified,
        double confidence,
        String riskLevel) {

    public GovernedMemoryDraft {
        if (scope == null) throw new IllegalArgumentException("MEMORY_SCOPE_REQUIRED");
        if (type == null || !type.persistable()) {
            throw new IllegalArgumentException("MEMORY_TYPE_NOT_PERSISTABLE");
        }
        scopeId = required(scopeId, "MEMORY_SCOPE_ID_REQUIRED");
        content = required(content, "MEMORY_CONTENT_REQUIRED");
        normalizedContent = required(normalizedContent, "MEMORY_NORMALIZED_CONTENT_REQUIRED");
        logicalKey = required(logicalKey, "MEMORY_LOGICAL_KEY_REQUIRED");
        sourceType = required(sourceType, "MEMORY_SOURCE_TYPE_REQUIRED");
        riskLevel = required(riskLevel, "MEMORY_RISK_LEVEL_REQUIRED");
        if (confidence < 0.0D || confidence > 1.0D) {
            throw new IllegalArgumentException("MEMORY_CONFIDENCE_INVALID");
        }
    }

    private static String required(String value, String code) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(code);
        return normalized;
    }
}
