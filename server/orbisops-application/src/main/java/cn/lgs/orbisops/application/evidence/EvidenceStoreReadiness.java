package cn.lgs.orbisops.application.evidence;

public record EvidenceStoreReadiness(
        String store,
        String status,
        boolean autoInit,
        boolean memoryFallbackAllowed,
        String reason,
        long recordCount) {

    public EvidenceStoreReadiness {
        store = required(store, "EVIDENCE_STORE_NAME_REQUIRED");
        status = required(status, "EVIDENCE_STORE_STATUS_REQUIRED");
        reason = reason == null ? "" : reason.trim();
        recordCount = Math.max(0L, recordCount);
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
