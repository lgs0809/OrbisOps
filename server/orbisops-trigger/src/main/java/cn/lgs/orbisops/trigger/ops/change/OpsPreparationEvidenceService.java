package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.toolset.OpsEvidenceStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Resolves only authoritative evidence persisted for the canonical run. */
final class OpsPreparationEvidenceService {

    private static final List<String> REFERENCE_KEYS = List.of(
            "evidenceId",
            "toolResultId",
            "outputHash",
            "fullOutputRef",
            "runId");

    private final Supplier<OpsEvidenceStore> evidenceStoreSupplier;

    OpsPreparationEvidenceService(Supplier<OpsEvidenceStore> evidenceStoreSupplier) {
        this.evidenceStoreSupplier = evidenceStoreSupplier;
    }

    EvidenceBundle resolve(String projectId, Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String runId = text(firstNonNull(
                safeRequest.get("runId"),
                safeRequest.get("sourceRunId")), "");
        if (runId.isBlank()) return EvidenceBundle.empty();

        OpsEvidenceStore store = evidenceStoreSupplier == null
                ? null
                : evidenceStoreSupplier.get();
        if (store == null) {
            throw new IllegalStateException(
                    "Evidence Store 未初始化，Prepare 不能信任 request evidence");
        }
        List<Map<String, Object>> trusted = store.listForRun(projectId, runId, 200).stream()
                .filter(this::completeTrustedReference)
                .map(this::immutableMap)
                .toList();
        List<Map<String, Object>> references = trusted.stream()
                .map(this::reference)
                .toList();
        return new EvidenceBundle(trusted, references);
    }

    private boolean completeTrustedReference(Map<String, Object> item) {
        String outputHash = text(item.get("outputHash"), "").toLowerCase();
        return Boolean.TRUE.equals(item.get("verified"))
                && !text(item.get("toolResultId"), "").isBlank()
                && outputHash.matches("[0-9a-f]{64}")
                && !text(item.get("fullOutputRef"), "").isBlank();
    }

    private Map<String, Object> reference(Map<String, Object> item) {
        Map<String, Object> ref = new LinkedHashMap<>();
        for (String key : REFERENCE_KEYS) {
            ref.put(key, text(item.get(key), ""));
        }
        return Collections.unmodifiableMap(ref);
    }

    private Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record EvidenceBundle(List<Map<String, Object>> trustedEvidence,
                          List<Map<String, Object>> evidenceRefs) {
        EvidenceBundle {
            trustedEvidence = trustedEvidence == null
                    ? List.of()
                    : List.copyOf(trustedEvidence);
            evidenceRefs = evidenceRefs == null
                    ? List.of()
                    : List.copyOf(evidenceRefs);
        }

        static EvidenceBundle empty() {
            return new EvidenceBundle(List.of(), List.of());
        }

        boolean present() {
            return !trustedEvidence.isEmpty();
        }
    }
}
