package cn.lgs.orbisops.domain.knowledge.rag.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Framework-neutral chunk candidate emitted by a format-specific parser.
 *
 * STRUCTURE_BOUNDED means the text represents a semantic block that may still
 * require paragraph-aware and hard splitting. EXACT means the parser already
 * selected the final evidence boundary and only index/id materialization is
 * required.
 */
public record RagChunkDraft(String text,
                            Map<String, Object> metadata,
                            Boundary boundary) {

    public RagChunkDraft {
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        boundary = boundary == null ? Boundary.EXACT : boundary;
    }

    public static RagChunkDraft structureBounded(String text, Map<String, Object> metadata) {
        return new RagChunkDraft(text, metadata, Boundary.STRUCTURE_BOUNDED);
    }

    public static RagChunkDraft exact(String text, Map<String, Object> metadata) {
        return new RagChunkDraft(text, metadata, Boundary.EXACT);
    }

    public enum Boundary {
        STRUCTURE_BOUNDED,
        EXACT
    }
}
