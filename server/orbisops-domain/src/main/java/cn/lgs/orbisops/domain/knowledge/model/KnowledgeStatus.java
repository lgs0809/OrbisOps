package cn.lgs.orbisops.domain.knowledge.model;

import java.util.Locale;

public enum KnowledgeStatus {
    ENABLED,
    DISABLED;

    public static KnowledgeStatus require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return ENABLED;
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("KNOWLEDGE_STATUS_UNKNOWN:" + normalized);
        }
    }
}
