package cn.lgs.orbisops.application.agentdefinition;

/** Outbound definition boundary required by the Agent clone use case. */
public interface AgentDefinitionClonePort<D> {

    String requireExistingProject(String projectId);

    D resolveSource(String sourceAgentId);

    D sanitizeForProject(D definition, String projectId);

    D prepareClone(
            D definition,
            String targetProjectId,
            String newAgentId,
            String newName);

    D normalizeExecutionShape(D definition);
}
