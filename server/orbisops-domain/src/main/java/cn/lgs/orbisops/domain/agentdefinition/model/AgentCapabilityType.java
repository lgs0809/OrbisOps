package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Arrays;

public enum AgentCapabilityType {
    SKILL("skill", AgentCapabilityScope.PROJECT_OR_ENABLED_GLOBAL),
    PROJECT_TOOL("project_tool", AgentCapabilityScope.PROJECT),
    EXECUTION_TARGET("execution_target", AgentCapabilityScope.PROJECT),
    KNOWLEDGE_BASE("knowledge_base", AgentCapabilityScope.PROJECT_OR_ENABLED_GLOBAL),
    INLINE_MCP_SERVER("inline_mcp_server", AgentCapabilityScope.INLINE);

    private final String storageValue;
    private final AgentCapabilityScope defaultScope;

    AgentCapabilityType(String storageValue, AgentCapabilityScope defaultScope) {
        this.storageValue = storageValue;
        this.defaultScope = defaultScope;
    }

    public String storageValue() {
        return storageValue;
    }

    public AgentCapabilityScope defaultScope() {
        return defaultScope;
    }

    public static AgentCapabilityType require(String value) {
        String normalized = value == null ? "" : value.trim();
        return Arrays.stream(values())
                .filter(type -> type.storageValue.equalsIgnoreCase(normalized)
                        || type.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        normalized.isBlank()
                                ? "AGENT_CAPABILITY_TYPE_REQUIRED"
                                : "AGENT_CAPABILITY_TYPE_UNKNOWN:" + normalized));
    }
}
