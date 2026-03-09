package cn.lgs.orbisops.domain.memory.model;

import java.util.Locale;

public enum MemoryScope {
    USER,
    PROJECT,
    SESSION;

    public static MemoryScope require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("MEMORY_SCOPE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MEMORY_SCOPE_UNKNOWN:" + normalized);
        }
    }
}
