package cn.lgs.orbisops.application.agentdefinition;

/** Outbound validation port for model and project knowledge references. */
public interface AgentModelKnowledgeReferenceValidationPort {

    void validateModel(String modelId, String owner);

    void validateKnowledge(String knowledgeBaseId, String owner, String projectId);
}
