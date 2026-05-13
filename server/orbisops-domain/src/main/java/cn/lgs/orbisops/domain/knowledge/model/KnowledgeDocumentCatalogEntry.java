package cn.lgs.orbisops.domain.knowledge.model;

import java.util.Map;

public record KnowledgeDocumentCatalogEntry(
        String documentId,
        KnowledgeBaseCatalogKey key,
        String fileName,
        String displayName,
        String sourceType,
        String documentType,
        long fileSize,
        String parseStatus,
        String vectorStatus,
        int chunkCount,
        boolean structurePreserved,
        String ingestionJobId,
        Map<String, Object> metadata
) {

    public KnowledgeDocumentCatalogEntry {
        documentId = required(documentId, "KNOWLEDGE_DOCUMENT_ID_REQUIRED");
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_KEY_REQUIRED");
        fileName = required(fileName, "KNOWLEDGE_DOCUMENT_FILE_NAME_REQUIRED");
        displayName = value(displayName);
        if (displayName.isBlank()) displayName = fileName;
        sourceType = value(sourceType);
        if (sourceType.isBlank()) sourceType = "UPLOAD";
        documentType = value(documentType);
        if (fileSize < 0L) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_SIZE_INVALID");
        parseStatus = required(parseStatus, "KNOWLEDGE_DOCUMENT_PARSE_STATUS_REQUIRED");
        vectorStatus = required(vectorStatus, "KNOWLEDGE_DOCUMENT_VECTOR_STATUS_REQUIRED");
        if (chunkCount < 0) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_CHUNK_COUNT_INVALID");
        ingestionJobId = value(ingestionJobId);
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
