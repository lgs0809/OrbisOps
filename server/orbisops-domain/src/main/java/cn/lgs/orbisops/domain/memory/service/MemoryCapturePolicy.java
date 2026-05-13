package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.CapturedMemoryMessage;
import cn.lgs.orbisops.domain.memory.model.MemoryCaptureDraft;

import java.util.LinkedHashMap;
import java.util.Map;

/** Domain policy that applies captured-message role and lifecycle metadata defaults. */
public class MemoryCapturePolicy {

    public CapturedMemoryMessage prepare(MemoryCaptureDraft draft,
                                         long nextTurnIndex,
                                         long createdAtEpochMillis,
                                         String createdAt) {
        if (draft == null || !draft.valid()) {
            throw new IllegalArgumentException("Memory capture draft requires sessionId and content");
        }
        Map<String, Object> metadata = new LinkedHashMap<>(draft.metadata());
        metadata.putIfAbsent("turn_index", Math.max(1L, nextTurnIndex));
        metadata.putIfAbsent("created_at_epoch_ms", Math.max(0L, createdAtEpochMillis));
        metadata.putIfAbsent("memory_status", "ACTIVE");
        return new CapturedMemoryMessage(
                draft.sessionId().trim(),
                trimToNull(draft.userId()),
                hasText(draft.role()) ? draft.role().trim() : "assistant",
                draft.content(),
                createdAt == null ? "" : createdAt,
                metadata);
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
