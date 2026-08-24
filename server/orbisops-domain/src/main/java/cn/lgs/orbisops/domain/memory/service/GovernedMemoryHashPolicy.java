package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.GovernedMemoryDraft;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.Map;
import java.text.Normalizer;
import cn.lgs.orbisops.domain.memory.model.GovernedMemorySnapshot;

/** Domain-owned SHA-256 identifiers for governed explicit-memory persistence. */
public class GovernedMemoryHashPolicy {

    public String idempotencyKey(GovernedMemoryDraft draft, String sourceRunId) {
        if (draft == null) throw new IllegalArgumentException("GOVERNED_MEMORY_DRAFT_REQUIRED");
        return CanonicalObjectHasher.sha256Text(draft.scope().name()
                + ":" + draft.scopeId()
                + ":" + draft.type().name()
                + ":" + draft.logicalKey()
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

    /** Equality of meaning-bearing content is independent of source Run and audit revision. */
    public String contentFingerprint(GovernedMemoryDraft draft) {
        return fingerprint(draft.scope().name(), draft.scopeId(), draft.type().name(), draft.logicalKey(), draft.normalizedContent());
    }

    public String contentFingerprint(GovernedMemorySnapshot snapshot) {
        return fingerprint(snapshot.scope().name(), snapshot.scopeId(), snapshot.type().name(), snapshot.logicalKey(), snapshot.normalizedContent());
    }

    private String fingerprint(String scope, String scopeId, String type, String key, String content) {
        return CanonicalObjectHasher.sha256(Map.of("scope", scope, "scopeId", scopeId, "type", type,
                "logicalKey", key, "content", Normalizer.normalize(value(content), Normalizer.Form.NFKC)
                        .replaceAll("(?U)\\s+", " ").trim()));
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
