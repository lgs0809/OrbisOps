package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework-neutral RAG chunk/search document exchanged across Domain ports. */
public record RagDocument(String id,
                          String text,
                          Map<String, Object> metadata) {

    public RagDocument {
        id = safe(id);
        text = raw(text);
        metadata = metadata == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (id.isBlank()) throw new IllegalArgumentException("RAG_DOCUMENT_ID_REQUIRED");
    }

    public RagDocument withMetadata(Map<String, Object> value) {
        return new RagDocument(id, text, value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String raw(String value) {
        return value == null ? "" : value;
    }
}
