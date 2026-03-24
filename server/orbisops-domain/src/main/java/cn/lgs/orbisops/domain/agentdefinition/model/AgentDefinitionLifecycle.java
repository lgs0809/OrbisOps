package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

public enum AgentDefinitionLifecycle {
    DRAFT,
    VALIDATED,
    PUBLISHED,
    DISABLED;

    public static AgentDefinitionLifecycle require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) throw new IllegalArgumentException("AGENT_DEFINITION_LIFECYCLE_REQUIRED");
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("AGENT_DEFINITION_LIFECYCLE_UNKNOWN:" + normalized);
        }
    }
}
