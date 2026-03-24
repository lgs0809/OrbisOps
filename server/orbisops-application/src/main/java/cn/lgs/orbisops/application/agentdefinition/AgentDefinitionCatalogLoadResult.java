package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Immutable report of one Agent Definition catalog initialization. */
public record AgentDefinitionCatalogLoadResult(
        String effectiveDefaultAgentId,
        int currentDefinitionCount,
        int storedCurrentDefinitionCount,
        List<String> rejectedProjectPlatformAgentIds,
        List<String> skippedStoredYamlCurrentAgentIds,
        List<String> skippedStoredYamlVersionKeys) {

    public AgentDefinitionCatalogLoadResult {
        effectiveDefaultAgentId = required(effectiveDefaultAgentId);
        rejectedProjectPlatformAgentIds = immutable(rejectedProjectPlatformAgentIds);
        skippedStoredYamlCurrentAgentIds = immutable(skippedStoredYamlCurrentAgentIds);
        skippedStoredYamlVersionKeys = immutable(skippedStoredYamlVersionKeys);
    }

    private static List<String> immutable(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String required(String value) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException("AGENT_DEFAULT_DEFINITION_REQUIRED");
        }
        return value.trim();
    }
}
