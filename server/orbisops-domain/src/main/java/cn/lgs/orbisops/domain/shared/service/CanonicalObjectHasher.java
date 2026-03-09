package cn.lgs.orbisops.domain.shared.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared deterministic SHA-256 helper for canonical objects and plain text. */
public final class CanonicalObjectHasher {

    private CanonicalObjectHasher() {
    }

    public static String sha256(Object value) {
        return sha256(value, Set.of());
    }

    public static String sha256(Object value, Set<String> ignoredRootKeys) {
        return sha256Text(CanonicalJson.stringify(
                withoutRootKeys(value, ignoredRootKeys)));
    }

    public static String sha256Text(String value) {
        return DigestUtils.sha256Hex(value == null ? "" : value);
    }

    private static Object withoutRootKeys(
            Object value,
            Set<String> ignoredRootKeys) {
        if (!(value instanceof Map<?, ?> map)
                || ignoredRootKeys == null
                || ignoredRootKeys.isEmpty()) {
            return value;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            String normalizedKey = String.valueOf(key);
            if (!ignoredRootKeys.contains(normalizedKey)) {
                filtered.put(normalizedKey, item);
            }
        });
        return filtered;
    }
}
