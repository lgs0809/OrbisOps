package cn.lgs.orbisops.domain.knowledge.model;

public record KnowledgeBaseCatalogEntry(
        Long id,
        KnowledgeBaseCatalogKey key,
        String name,
        String description,
        KnowledgeStatus status,
        long documentCount,
        long chunkCount,
        String sourceType,
        String retrievalPolicyJson,
        String createBy,
        String createdAt,
        String updatedAt
) {

    public KnowledgeBaseCatalogEntry {
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_KEY_REQUIRED");
        name = required(name, "KNOWLEDGE_BASE_NAME_REQUIRED");
        description = value(description);
        if (status == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_REQUIRED");
        if (documentCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_COUNT_INVALID");
        if (chunkCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_CHUNK_COUNT_INVALID");
        sourceType = value(sourceType);
        if (sourceType.isBlank()) sourceType = "DB";
        retrievalPolicyJson = value(retrievalPolicyJson);
        createBy = value(createBy);
        createdAt = value(createdAt);
        updatedAt = value(updatedAt);
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
