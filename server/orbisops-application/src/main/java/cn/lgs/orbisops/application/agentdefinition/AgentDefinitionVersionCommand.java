package cn.lgs.orbisops.application.agentdefinition;

/** Validated identity of one immutable Agent Definition version operation. */
public record AgentDefinitionVersionCommand(
        String agentId,
        int version) {

    public AgentDefinitionVersionCommand(String agentId, Integer version) {
        this(agentId == null ? "" : agentId.trim(), requireVersion(version));
    }

    public AgentDefinitionVersionCommand {
        agentId = agentId == null ? "" : agentId.trim();
        if (version <= 0) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_INVALID");
        }
    }

    private static int requireVersion(Integer version) {
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("AGENT_DEFINITION_VERSION_INVALID");
        }
        return version;
    }
}
