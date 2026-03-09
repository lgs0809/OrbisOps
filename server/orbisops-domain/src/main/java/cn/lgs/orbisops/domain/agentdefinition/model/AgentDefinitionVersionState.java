package cn.lgs.orbisops.domain.agentdefinition.model;

public record AgentDefinitionVersionState(String agentId,
                                          int version,
                                          String definitionHash,
                                          String projectId,
                                          AgentDefinitionLifecycle lifecycle) {

    public AgentDefinitionVersionState {
        agentId = required(agentId, "AGENT_DEFINITION_ID_REQUIRED");
        if (version <= 0) throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_INVALID");
        definitionHash = value(definitionHash);
        projectId = value(projectId);
        if (lifecycle == null) throw new IllegalArgumentException("AGENT_DEFINITION_LIFECYCLE_REQUIRED");
    }

    private static String required(String input, String error) {
        String value = value(input);
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
