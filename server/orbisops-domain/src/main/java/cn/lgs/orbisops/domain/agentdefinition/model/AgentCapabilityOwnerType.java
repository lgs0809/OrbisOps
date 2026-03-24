package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

public enum AgentCapabilityOwnerType {
    AGENT,
    NODE,
    AGENTSCOPE;

    public static AgentCapabilityOwnerType require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_OWNER_TYPE_REQUIRED");
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_OWNER_TYPE_UNKNOWN:" + normalized);
        }
    }
}
