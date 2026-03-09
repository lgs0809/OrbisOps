package cn.lgs.orbisops.domain.source.model;

import java.util.Locale;

public enum BuildProfile {
    MAVEN_VERIFY,
    NPM_TEST_BUILD,
    MAKE_CI;

    public static BuildProfile require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("SOURCE_BUILD_PROFILE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("SOURCE_BUILD_PROFILE_UNKNOWN:" + normalized);
        }
    }
}
