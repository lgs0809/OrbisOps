package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

public enum AgentCapabilityScope {
    INLINE,
    PROJECT,
    PROJECT_OR_ENABLED_GLOBAL;

    public static AgentCapabilityScope require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_SCOPE_REQUIRED");
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_SCOPE_UNKNOWN:" + normalized);
        }
    }
}
