package cn.lgs.orbisops.domain.memory.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Framework-neutral semantic memory document. */
public record SemanticMemoryDocumentSnapshot(
        String id,
        String content,
        Map<String, Object> metadata) {

    public SemanticMemoryDocumentSnapshot {
        id = value(id);
        content = value(content);
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
