package cn.lgs.orbisops.trigger.application.security;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Immutable authentication settings for JWT and service-token verification. */
public record AdminAuthSettings(
        String jwtSecret,
        long jwtTtlHours,
        String serviceToken,
        boolean rejectWeakSecrets) {

    public AdminAuthSettings {
        jwtSecret = jwtSecret == null ? "" : jwtSecret;
        jwtTtlHours = Math.max(1, Math.min(jwtTtlHours, 168));
        serviceToken = serviceToken == null ? "" : serviceToken;
    }

    public boolean matchesServiceCredential(String provided) {
        return matchesServiceCredential(serviceToken, provided);
    }

    public static boolean matchesServiceCredential(String configuredValue, String provided) {
        if (!StringUtils.hasText(provided) || !StringUtils.hasText(configuredValue)) {
            return false;
        }
        byte[] configured = configuredValue.trim().getBytes(StandardCharsets.UTF_8);
        byte[] candidate = provided.trim().getBytes(StandardCharsets.UTF_8);
        return configured.length == candidate.length
                && MessageDigest.isEqual(configured, candidate);
    }

    public boolean hasWeakOrMissingJwtSecret() {
        return !StringUtils.hasText(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32;
    }

    public static AdminAuthSettings defaults() {
        return new AdminAuthSettings("", 12, "", true);
    }
}
