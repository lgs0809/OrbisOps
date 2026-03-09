package cn.lgs.orbisops.domain.agentdefinition.model;

/** Immutable persistence snapshot for one Agent Definition version. */
public record AgentDefinitionSnapshot(String agentId,
                                      int version,
                                      String definitionHash,
                                      AgentDefinitionLifecycle lifecycle,
                                      String name,
                                      String projectId,
                                      String engine,
                                      String description,
                                      String instruction,
                                      String startNodeId,
                                      String definitionJson,
                                      boolean enabled,
                                      boolean currentPublished,
                                      String source) {

    public AgentDefinitionSnapshot {
        agentId = required(agentId, "AGENT_DEFINITION_ID_REQUIRED");
        if (version <= 0) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_INVALID");
        }
        definitionHash = value(definitionHash);
        if (lifecycle == null) {
            throw new IllegalArgumentException("AGENT_DEFINITION_LIFECYCLE_REQUIRED");
        }
        name = value(name);
        projectId = value(projectId);
        engine = value(engine);
        description = value(description);
        instruction = value(instruction);
        startNodeId = value(startNodeId);
        definitionJson = required(definitionJson, "AGENT_DEFINITION_SNAPSHOT_REQUIRED");
        source = value(source);
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
