package cn.lgs.orbisops.domain.shared.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
