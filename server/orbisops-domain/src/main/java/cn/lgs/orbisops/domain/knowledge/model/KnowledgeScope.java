package cn.lgs.orbisops.domain.knowledge.model;

import java.util.Locale;

public enum KnowledgeScope {
    GLOBAL,
    PROJECT;

    public static KnowledgeScope require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_SCOPE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("KNOWLEDGE_SCOPE_UNKNOWN:" + normalized);
        }
    }
}
