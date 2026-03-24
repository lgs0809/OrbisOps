package cn.lgs.orbisops.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.service.AgentMcpServerDefinitionPolicy;

/** Coordinates validation of every external and inline resource referenced by an Agent definition. */
public final class AgentDefinitionResourceValidationUseCase {

    private final AgentCapabilityReferenceValidationPort capabilityPort;
    private final AgentModelKnowledgeReferenceValidationPort modelKnowledgePort;
    private final AgentMcpServerDefinitionPolicy inlineMcpPolicy;

    public AgentDefinitionResourceValidationUseCase(
            AgentCapabilityReferenceValidationPort capabilityPort,
            AgentModelKnowledgeReferenceValidationPort modelKnowledgePort,
            AgentMcpServerDefinitionPolicy inlineMcpPolicy) {
        if (capabilityPort == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_REFERENCE_VALIDATION_PORT_REQUIRED");
        }
        if (modelKnowledgePort == null) {
            throw new IllegalArgumentException(
                    "AGENT_MODEL_KNOWLEDGE_VALIDATION_PORT_REQUIRED");
        }
        if (inlineMcpPolicy == null) {
            throw new IllegalArgumentException(
                    "AGENT_INLINE_MCP_DEFINITION_POLICY_REQUIRED");
        }
        this.capabilityPort = capabilityPort;
        this.modelKnowledgePort = modelKnowledgePort;
        this.inlineMcpPolicy = inlineMcpPolicy;
    }

    public void validate(AgentDefinitionResourceValidationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_RESOURCE_VALIDATION_REQUEST_REQUIRED");
        }
        capabilityPort.validateProject(request.projectId());
        for (AgentDefinitionResourceValidationRequest.ResourceOwner owner : request.owners()) {
            validateOwner(owner, request.projectId());
        }
    }

    private void validateOwner(
            AgentDefinitionResourceValidationRequest.ResourceOwner owner,
            String projectId) {
        if (owner == null) return;
        capabilityPort.validateSkills(owner.skills(), owner.owner(), projectId);
        capabilityPort.validateMcpReferences(
                owner.mcpIds(),
                owner.owner() + " MCP",
                projectId);
        modelKnowledgePort.validateModel(
                owner.modelId(),
                owner.owner() + " modelId");
        modelKnowledgePort.validateKnowledge(
                owner.knowledgeBaseId(),
                owner.owner() + " knowledgeBaseId",
                projectId);
        inlineMcpPolicy.validate(owner.inlineMcpServers(), owner.owner());
    }
}
