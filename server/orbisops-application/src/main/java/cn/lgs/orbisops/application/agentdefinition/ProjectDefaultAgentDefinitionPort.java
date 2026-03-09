package cn.lgs.orbisops.application.agentdefinition;

import java.util.List;

/** Outbound definition boundary required by the project default Agent bootstrap process. */
public interface ProjectDefaultAgentDefinitionPort<D> {

    String requireExistingProject(String projectId);

    List<D> versions(String agentId);

    D loadDefaultTemplate();

    D sanitizeForProject(D definition, String projectId);

    D prepareDraft(
            D definition,
            String projectId,
            String agentId,
            String agentName);

    D normalizeExecutionShape(D definition);

    ProjectDefaultAgentDefinitionFacts facts(D definition);
}
