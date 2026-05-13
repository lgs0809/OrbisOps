package cn.lgs.orbisops.domain.knowledge.model;

import java.util.Map;

public record KnowledgeChunkCatalogEntry(
        String chunkId,
        String documentId,
        KnowledgeBaseCatalogKey key,
        int chunkIndex,
        String chunkStrategy,
        String contentPreview,
        String parseStatus,
        String vectorStatus,
        Map<String, Object> metadata
) {

    public KnowledgeChunkCatalogEntry {
        chunkId = required(chunkId, "KNOWLEDGE_CHUNK_ID_REQUIRED");
        documentId = required(documentId, "KNOWLEDGE_DOCUMENT_ID_REQUIRED");
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_KEY_REQUIRED");
        if (chunkIndex < 0) throw new IllegalArgumentException("KNOWLEDGE_CHUNK_INDEX_INVALID");
        chunkStrategy = value(chunkStrategy);
        contentPreview = value(contentPreview);
        parseStatus = required(parseStatus, "KNOWLEDGE_CHUNK_PARSE_STATUS_REQUIRED");
        vectorStatus = required(vectorStatus, "KNOWLEDGE_CHUNK_VECTOR_STATUS_REQUIRED");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
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
