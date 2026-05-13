package cn.lgs.orbisops.domain.knowledge.rag.service;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Materializes ordered, framework-neutral chunk drafts into stable RAG documents.
 *
 * The service owns shared ingestion invariants: metadata overlay, paragraph-aware
 * bounding, oversized structural-block splitting, overlap, chunk indexes and
 * deterministic identifiers. Format parsers remain responsible only for finding
 * semantic boundaries and attaching format-specific metadata.
 */
public final class RagChunkMaterializer {

    private static final int MIN_SEGMENT_CHARS = 1000;
    private static final int MAX_SEGMENT_CHARS = 12000;

    private final int defaultMaxSegmentChars;

    public RagChunkMaterializer() {
        this(RagParsePolicy.DEFAULT_MAX_SEGMENT_CHARS);
    }

    public RagChunkMaterializer(int defaultMaxSegmentChars) {
        this.defaultMaxSegmentChars = clamp(defaultMaxSegmentChars, MIN_SEGMENT_CHARS, MAX_SEGMENT_CHARS);
    }

    public List<RagDocument> materialize(List<RagChunkDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        MaterializationState state = new MaterializationState();
        for (RagChunkDraft draft : drafts) {
            if (draft == null) {
                continue;
            }
            if (draft.boundary() == RagChunkDraft.Boundary.STRUCTURE_BOUNDED) {
                appendBounded(state, draft.text(), draft.metadata());
            } else {
                appendDocument(state, draft.text(), draft.metadata());
            }
        }
        return new ArrayList<>(state.documents);
    }

    public Map<String, Object> mergeMetadata(Map<String, Object> metadata, Object... pairs) {
        Map<String, Object> merged = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        if (pairs == null) {
            return merged;
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                merged.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return merged;
    }

    public int maxSegmentChars(Map<String, Object> metadata) {
        Object configured = metadata == null ? null : metadata.get("max_segment_chars");
        if (configured instanceof Number number) {
            return clamp(number.intValue(), MIN_SEGMENT_CHARS, MAX_SEGMENT_CHARS);
        }
        return defaultMaxSegmentChars;
    }

    private void appendBounded(MaterializationState state, String text, Map<String, Object> metadata) {
        String normalized = normalize(text).trim();
        if (normalized.isBlank()) {
            return;
        }
        int maxSegmentChars = maxSegmentChars(metadata);
        if (normalized.length() <= maxSegmentChars) {
            appendDocument(state, normalized, metadata);
            return;
        }

        String[] blocks = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        int part = 1;
        for (String block : blocks) {
            if (block.length() > maxSegmentChars) {
                if (!current.isEmpty()) {
                    appendDocument(state, current.toString(), mergeMetadata(metadata, "chunk_part", part++));
                    current.setLength(0);
                }
                appendOversizedBlock(state, block, metadata, part);
                part += Math.max(1, block.length() / maxSegmentChars);
                continue;
            }
            if (current.length() + block.length() + 2 > maxSegmentChars && !current.isEmpty()) {
                appendDocument(state, current.toString(), mergeMetadata(metadata, "chunk_part", part++));
                current.setLength(0);
            }
            current.append(block).append("\n\n");
        }
        if (!current.isEmpty()) {
            appendDocument(state, current.toString(), mergeMetadata(metadata, "chunk_part", part));
        }
    }

    private void appendOversizedBlock(MaterializationState state,
                                      String block,
                                      Map<String, Object> metadata,
                                      int partStart) {
        int start = 0;
        int part = partStart;
        int maxSegmentChars = maxSegmentChars(metadata);
        int overlapChars = hardSplitOverlapChars(metadata, maxSegmentChars);
        while (start < block.length()) {
            int end = Math.min(block.length(), start + maxSegmentChars);
            appendDocument(state, block.substring(start, end), mergeMetadata(metadata,
                    "chunk_part", part++,
                    "secondary_split", true,
                    "split_reason", "oversized_structural_block",
                    "hard_split_overlap_chars", overlapChars));
            if (end >= block.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlapChars);
        }
    }

    private void appendDocument(MaterializationState state, String text, Map<String, Object> metadata) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return;
        }
        int index = state.nextIndex++;
        Map<String, Object> indexedMetadata = mergeMetadata(metadata, "chunk_index", index);
        state.documents.add(new RagDocument(stableId(trimmed, indexedMetadata, index), trimmed, indexedMetadata));
    }

    private String stableId(String text, Map<String, Object> metadata, int index) {
        String idSeed = metadata.getOrDefault("knowledge_scope", "GLOBAL")
                + ":" + metadata.getOrDefault("project_id", "")
                + ":" + metadata.getOrDefault("knowledge", "")
                + ":" + metadata.getOrDefault("rag_name", "")
                + ":" + metadata.getOrDefault("source", "")
                + ":" + index
                + ":" + text.hashCode();
        return UUID.nameUUIDFromBytes(idSeed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private int hardSplitOverlapChars(Map<String, Object> metadata, int maxSegmentChars) {
        Object configured = metadata == null ? null : metadata.get("hard_split_overlap_chars");
        int value = configured instanceof Number number ? number.intValue() : 0;
        return clamp(value, 0, Math.max(0, maxSegmentChars / 2));
    }

    private String normalize(String value) {
        String raw = value == null ? "" : value;
        if (raw.startsWith("\uFEFF")) {
            raw = raw.substring(1);
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class MaterializationState {
        private final List<RagDocument> documents = new ArrayList<>();
        private int nextIndex;
    }
}
