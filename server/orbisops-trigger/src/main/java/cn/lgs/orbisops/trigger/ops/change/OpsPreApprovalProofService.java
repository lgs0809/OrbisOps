package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.toolset.OpsTrustedProofService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded adapter for trusted-proof verification, recording, and report references. */
final class OpsPreApprovalProofService {

    private static final OpsPreApprovalStructuredValueReader STRUCTURED_VALUE_READER =
            new OpsPreApprovalStructuredValueReader();

    private final OpsTrustedProofService trustedProofService;

    OpsPreApprovalProofService(OpsTrustedProofService trustedProofService) {
        this.trustedProofService = trustedProofService;
    }

    boolean available() {
        return trustedProofService != null;
    }

    boolean trusted(String proofType,
                    Map<String, Object> snapshot,
                    String externalRunId) {
        if (!available()) return false;
        return trustedProofService.verifyTrustedProof(
                text(snapshot.get("projectId"), ""),
                text(snapshot.get("packageId"), ""),
                intValue(snapshot.get("version"), 0),
                text(snapshot.get("packageHash"), ""),
                text(snapshot.get("riskLevel"), "MEDIUM"),
                proofType,
                text(externalRunId, ""));
    }

    String firstTrustedType(Map<String, Object> snapshot,
                            List<String> proofTypes) {
        for (String proofType : proofTypes) {
            if (trusted(proofType, snapshot, "")) return proofType;
        }
        return "";
    }

    Map<String, Object> record(Map<String, Object> snapshot,
                               String riskLevel,
                               String proofType,
                               String source,
                               String externalRunId,
                               Map<String, Object> metadata,
                               String actor) {
        if (!available()) throw new IllegalStateException("TRUSTED_PROOF_SERVICE_UNAVAILABLE");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", text(snapshot.get("projectId"), ""));
        payload.put("packageId", text(snapshot.get("packageId"), ""));
        payload.put("packageVersion", intValue(snapshot.get("version"), 0));
        payload.put("packageHash", text(snapshot.get("packageHash"), ""));
        payload.put("riskLevel", text(riskLevel, text(snapshot.get("riskLevel"), "MEDIUM")));
        payload.put("proofType", proofType);
        payload.put("source", source);
        payload.put("externalRunId", text(externalRunId, ""));
        payload.put("resultStatus", "PASSED");
        payload.put("metadata", metadata == null ? Map.of() : metadata);
        return trustedProofService.recordTrustedProof(payload, actor);
    }

    Map<String, Object> sourceRef(Map<String, Object> proof) {
        Map<String, Object> safeProof = proof == null ? Map.of() : proof;
        Map<String, Object> metadata = STRUCTURED_VALUE_READER.object(
                firstNonNull(safeProof.get("metadata"), safeProof.get("metadataJson")));
        return sourceRef(
                text(safeProof.get("proofType"), ""),
                firstNonBlank(safeProof.get("externalRunId"), safeProof.get("proofId")),
                Map.of(
                        "proofId", text(safeProof.get("proofId"), ""),
                        "outputHash", text(metadata.get("outputHash"), "")));
    }

    Map<String, Object> sourceRef(String proofType,
                                  String externalRunId,
                                  Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("proofType", proofType);
        ref.put("externalRunId", text(externalRunId, ""));
        ref.put("proofId", text(safeMetadata.get("proofId"), ""));
        ref.put("outputHash", text(
                safeMetadata.get("outputHash"),
                text(safeMetadata.get("testProofHash"), "")));
        return ref;
    }

    private String firstNonBlank(Object... values) {
        if (values != null) {
            for (Object value : values) {
                String candidate = text(value, "");
                if (!candidate.isBlank()) return candidate;
            }
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value, ""));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
