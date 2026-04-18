package cn.lgs.orbisops.domain.changepackage.model;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** User/API landing request bound to the frozen approved pointer. */
public record ChangePackageLandingRequest(int approvedVersion,
                                          String approvedPackageHash,
                                          String idempotencyKey,
                                          Map<String, Object> values) {

    public ChangePackageLandingRequest {
        if (approvedVersion <= 0) throw new IllegalArgumentException("CHANGE_PACKAGE_LANDING_VERSION_INVALID");
        approvedPackageHash = required(approvedPackageHash, "CHANGE_PACKAGE_LANDING_HASH_REQUIRED");
        idempotencyKey = text(idempotencyKey);
        values = values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
        if (!text(values.get("landingGraphId")).isBlank()) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_GRAPH_NOT_USER_EDITABLE");
        }
    }

    public static ChangePackageLandingRequest from(ChangePackagePointer pointer, Map<String, Object> request) {
        if (pointer == null || !pointer.approved()) {
            throw new IllegalStateException("CHANGE_PACKAGE_APPROVAL_REQUIRED");
        }
        Map<String, Object> safe = request == null ? Map.of() : request;
        int version = intValue(safe.get("version"));
        String hash = firstNonBlank(safe.get("packageHash"), safe.get("approvedPackageHash"));
        if (version != pointer.approvedVersion()) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_VERSION_MISMATCH");
        }
        if (!pointer.approvedPackageHash().equals(hash)) {
            throw new IllegalStateException("CHANGE_PACKAGE_LANDING_HASH_MISMATCH");
        }
        return new ChangePackageLandingRequest(version, hash, text(safe.get("idempotencyKey")), safe);
    }

    public String effectiveIdempotencyKey(String packageId) {
        String normalizedPackageId = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        if (idempotencyKey.isBlank()) {
            // Preserve the historical deterministic key so existing LandingRun rows remain reusable.
            return "landing:" + normalizedPackageId + ":"
                    + approvedVersion + ":" + approvedPackageHash;
        }
        // Client keys are only request correlation material. Namespace them to the frozen
        // package pointer before using the globally unique LandingRun idempotency index.
        return "landing:custom:" + CanonicalObjectHasher.sha256(Map.of(
                "packageId", normalizedPackageId,
                "approvedVersion", approvedVersion,
                "approvedPackageHash", approvedPackageHash,
                "clientIdempotencyKey", idempotencyKey));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String candidate = text(value);
            if (!candidate.isBlank()) return candidate;
        }
        return "";
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
