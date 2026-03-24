package cn.lgs.orbisops.application.agentdefinition;

/** Command for cloning one Agent Definition into a target project as a new draft identity. */
public record AgentDefinitionCloneCommand(
        String sourceAgentId,
        String targetProjectId,
        String newAgentId,
        String newName) {

    public AgentDefinitionCloneCommand {
        sourceAgentId = required(sourceAgentId, "sourceAgentId");
        targetProjectId = required(targetProjectId, "targetProjectId");
        newAgentId = required(newAgentId, "newAgentId");
        newName = newName == null ? "" : newName.trim();
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "复制 Agent 必须提供 sourceAgentId、targetProjectId 和 newAgentId");
        }
        return normalized;
    }
}
