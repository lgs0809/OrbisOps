package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.Map;

/** Domain-owned SHA-256 identifiers for governed explicit-memory persistence. */
public class GovernedMemoryHashPolicy {

    public String idempotencyKey(GovernedMemoryDraft draft, String sourceRunId) {
        if (draft == null) throw new IllegalArgumentException("GOVERNED_MEMORY_DRAFT_REQUIRED");
        return CanonicalObjectHasher.sha256Text(draft.scope().name()
                + ":" + draft.scopeId()
                + ":" + draft.type().name()
                + ":" + value(sourceRunId)
                + ":" + draft.normalizedContent());
    }

    public String memoryHash(GovernedMemoryDraft draft, int version) {
        if (draft == null) throw new IllegalArgumentException("GOVERNED_MEMORY_DRAFT_REQUIRED");
        return CanonicalObjectHasher.sha256(Map.of(
                "scopeType", draft.scope().name(),
                "scopeId", draft.scopeId(),
                "memoryType", draft.type().name(),
                "logicalKey", draft.logicalKey(),
                "version", version,
                "content", draft.normalizedContent()));
    }

    public String memoryHash(String canonicalSnapshotJson) {
        String canonical = value(canonicalSnapshotJson);
        if (canonical.isBlank()) {
            throw new IllegalArgumentException("GOVERNED_MEMORY_CANONICAL_SNAPSHOT_REQUIRED");
        }
        return CanonicalObjectHasher.sha256Text(canonical);
    }

    public boolean conflicts(String existingHash, String incomingHash) {
        return !value(existingHash).equals(value(incomingHash));
    }

    public String sha256(String value) {
        return CanonicalObjectHasher.sha256Text(value(value));
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
