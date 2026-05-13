package cn.lgs.orbisops.domain.memory.model;

import java.util.Locale;

public enum MemoryType {
    USER_PREFERENCE,
    USER_WORKFLOW,
    USER_DOMAIN_FOCUS,
    PROJECT_CONTEXT,
    PROJECT_CONVENTION,
    PROJECT_GLOSSARY,
    SESSION_CONTEXT,
    PROJECT_FACT,
    PROJECT_HINT,
    UNCLASSIFIED;

    public static MemoryType require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("MEMORY_TYPE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("MEMORY_TYPE_UNKNOWN:" + normalized);
        }
    }

    public boolean persistable() {
        return this != UNCLASSIFIED;
    }
}
