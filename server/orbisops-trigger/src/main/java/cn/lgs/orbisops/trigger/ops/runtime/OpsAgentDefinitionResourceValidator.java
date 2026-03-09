package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionResourceValidationUseCase;
import org.springframework.stereotype.Component;

/** Trigger facade that maps Agent DTOs into the application resource-validation use case. */
@Component
public final class OpsAgentDefinitionResourceValidator {

    private final OpsAgentGraphDefinitionMapper mapper;
    private final AgentDefinitionResourceValidationUseCase useCase;

    public OpsAgentDefinitionResourceValidator(
            OpsAgentProjectCapabilityReferenceValidator capabilityValidator,
            OpsAgentModelKnowledgeReferenceValidator modelKnowledgeValidator,
            OpsAgentMcpServerDefinitionPolicy mcpServerPolicy) {
        if (capabilityValidator == null) {
            throw new IllegalArgumentException(
                    "AGENT_CAPABILITY_REFERENCE_VALIDATOR_REQUIRED");
        }
        if (modelKnowledgeValidator == null) {
            throw new IllegalArgumentException(
                    "AGENT_MODEL_KNOWLEDGE_VALIDATOR_REQUIRED");
        }
        if (mcpServerPolicy == null) {
            throw new IllegalArgumentException(
                    "AGENT_MCP_SERVER_DEFINITION_POLICY_REQUIRED");
        }
        this.mapper = new OpsAgentGraphDefinitionMapper();
        this.useCase = new AgentDefinitionResourceValidationUseCase(
                capabilityValidator,
                modelKnowledgeValidator,
                mcpServerPolicy.domainPolicy());
    }

    public void validate(OpsAgentDefinition definition) {
        useCase.validate(mapper.resources(definition));
    }
}
