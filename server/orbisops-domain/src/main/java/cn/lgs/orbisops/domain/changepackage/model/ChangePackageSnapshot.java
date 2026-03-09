package cn.lgs.orbisops.domain.changepackage.model;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageCanonicalHasher;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.Map;

/** Immutable, hash-sealed ChangePackage version snapshot. */
public record ChangePackageSnapshot(Map<String, Object> values, String packageHash) {

    public ChangePackageSnapshot {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("CHANGE_PACKAGE_SNAPSHOT_REQUIRED");
        packageHash = required(packageHash, "CHANGE_PACKAGE_SNAPSHOT_HASH_REQUIRED");
        Map<String, Object> copy = mutableCopy(values);
        String embeddedHash = text(copy.get("packageHash"));
        if (!embeddedHash.isBlank() && !packageHash.equals(embeddedHash)) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_SNAPSHOT_HASH_MISMATCH");
        }
        copy.put("packageHash", packageHash);
        values = Map.copyOf(copy);
    }

    public static ChangePackageSnapshot seal(Map<String, Object> values) {
        Map<String, Object> copy = mutableCopy(values);
        copy.remove("packageHash");
        String hash = ChangePackageCanonicalHasher.packageHash(copy);
        copy.put("packageHash", hash);
        return new ChangePackageSnapshot(copy, hash);
    }

    public Map<String, Object> toMap() {
        return mutableCopy(values);
    }

    public ChangePackageCurrentState currentState() {
        return ChangePackageCurrentState.fromSnapshot(values);
    }

    public ChangePackageStatus status() {
        return ChangePackageStatus.require(text(values.getOrDefault(
                "status", ChangePackageStatus.DRAFT.name())));
    }

    public ChangePackageType packageType() {
        return ChangePackageType.require(text(values.getOrDefault(
                "packageType", ChangePackageType.MANUAL_REQUIRED.name())));
    }

    public String sessionId() {
        return text(values.get("sessionId"));
    }

    public String incidentId() {
        return text(values.get("incidentId"));
    }

    public String preparationAgentId() {
        return text(values.get("preparationAgentId"));
    }

    public int preparationAgentVersion() {
        return number(values.get("preparationAgentVersion"));
    }

    private static Map<String, Object> mutableCopy(Map<String, Object> source) {
        return CanonicalJson.copyObject(source);
    }

    private static String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static int number(Object value) {
        try {
            return value instanceof Number number
                    ? Math.max(0, number.intValue())
                    : Math.max(0, Integer.parseInt(text(value)));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
