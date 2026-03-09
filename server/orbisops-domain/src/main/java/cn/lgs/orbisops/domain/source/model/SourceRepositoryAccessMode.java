package cn.lgs.orbisops.domain.source.model;

import java.util.Locale;

/** Controls where repository operations execute; it is not an authorization role. */
public enum SourceRepositoryAccessMode {
    LOCAL,
    MCP;

    public static SourceRepositoryAccessMode require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return LOCAL;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("SOURCE_REPOSITORY_ACCESS_MODE_INVALID");
        }
    }
}
