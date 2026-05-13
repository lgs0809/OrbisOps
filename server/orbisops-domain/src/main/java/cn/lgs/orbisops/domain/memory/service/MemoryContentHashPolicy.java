package cn.lgs.orbisops.domain.memory.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Compatibility policy for deterministic non-security memory content identifiers. */
public class MemoryContentHashPolicy {

    public String stableHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value(content).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("JDK MD5 digest is unavailable", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(Character.forDigit((current >>> 4) & 0x0F, 16));
            value.append(Character.forDigit(current & 0x0F, 16));
        }
        return value.toString();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
