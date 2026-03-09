package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.util.LinkedHashMap;
import java.util.Map;

/** Typed identity envelope around the open preparation snapshot input. */
public record ChangePackagePreparationPlan(
        String projectId,
        String requestedPackageId,
        Map<String, Object> snapshotInput,
        Map<String, Object> trustedProofs
) {

    private static final String TRUSTED_PROOFS_KEY = "__trustedPreparationProofs";

    public ChangePackagePreparationPlan {
        projectId = required(projectId, "CHANGE_PACKAGE_PROJECT_ID_REQUIRED");
        requestedPackageId = text(requestedPackageId);
        snapshotInput = immutable(snapshotInput);
        trustedProofs = immutable(trustedProofs);
        if (!projectId.equals(text(snapshotInput.get("projectId")))) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_PROJECT_MISMATCH");
        }
        String embeddedPackageId = text(snapshotInput.get("packageId"));
        if (!requestedPackageId.isBlank()
                && !embeddedPackageId.isBlank()
                && !requestedPackageId.equals(embeddedPackageId)) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_PREPARATION_PACKAGE_MISMATCH");
        }
    }

    public static ChangePackagePreparationPlan from(Map<String, Object> source) {
        Map<String, Object> input = immutable(source);
        Map<String, Object> trustedProofs = objectMap(input.get(TRUSTED_PROOFS_KEY));
        Map<String, Object> snapshotInput = new LinkedHashMap<>(input);
        snapshotInput.remove(TRUSTED_PROOFS_KEY);
        return new ChangePackagePreparationPlan(
                text(snapshotInput.get("projectId")),
                text(snapshotInput.get("packageId")),
                snapshotInput,
                trustedProofs);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return immutable(result);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Map.copyOf(CanonicalJson.copyObject(source));
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
