package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionValidationPoliciesTest {

    @Test
    void projectCapabilityValidatorMustAcceptSourceBackedMcp() {
        IAiClientToolMcpConfigRepository mcpRepository =
                mock(IAiClientToolMcpConfigRepository.class);
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        ProjectMcpRuntimeDescriptorApplicationService descriptor =
                mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        OpsSourceRepositoryService sourceRepository =
                mock(OpsSourceRepositoryService.class);
        when(mcpRepository.queryByMcpId("source-mcp")).thenReturn(null);
        when(authorization.allows("project-a", "source-mcp")).thenReturn(false);
        when(descriptor.existsEnabledAny("source-mcp")).thenReturn(false);
        when(sourceRepository.resolveMcpServer("project-a", "source-mcp"))
                .thenReturn(Optional.of(OpsMcpServerConfig.builder()
                        .name("source-mcp")
                        .transport("stdio")
                        .command("node")
                        .build()));
        OpsAgentProjectCapabilityReferenceValidator validator =
                new OpsAgentProjectCapabilityReferenceValidator(
                        () -> mcpRepository,
                        () -> null,
                        () -> null,
                        () -> authorization,
                        () -> null,
                        () -> descriptor,
                        () -> sourceRepository);

        assertDoesNotThrow(() -> validator.validateMcpReferences(
                List.of("source-mcp"), "Agent MCP", "project-a"));
    }

    @Test
    void projectCapabilityValidatorMustRejectMcpOwnedByAnotherProject() {
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        ProjectMcpRuntimeDescriptorApplicationService descriptor =
                mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        when(authorization.allows("project-a", "foreign-mcp")).thenReturn(false);
        when(descriptor.existsEnabledAny("foreign-mcp")).thenReturn(true);
        OpsAgentProjectCapabilityReferenceValidator validator =
                new OpsAgentProjectCapabilityReferenceValidator(
                        () -> null,
                        () -> null,
                        () -> null,
                        () -> authorization,
                        () -> null,
                        () -> descriptor,
                        () -> null);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateMcpReferences(
                        List.of("foreign-mcp"), "Agent MCP", "project-a"));
    }

    @Test
    void modelKnowledgeValidatorMustUseKnowledgeTagFallback() {
        IAiClientModelConfigRepository modelRepository =
                mock(IAiClientModelConfigRepository.class);
        IAiClientRagOrderConfigRepository ragRepository =
                mock(IAiClientRagOrderConfigRepository.class);
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(modelRepository.queryByModelId("model-a")).thenReturn(
                AiClientConfigRecord.builder().modelId("model-a").status(1).build());
        when(authorization.allows("project-a", "knowledge-a")).thenReturn(true);
        when(ragRepository.queryByRagId("knowledge-a")).thenReturn(null);
        when(ragRepository.queryByKnowledgeTag("knowledge-a")).thenReturn(List.of(
                AiClientConfigRecord.builder()
                        .knowledgeTag("knowledge-a")
                        .status(1)
                        .build()));
        OpsAgentModelKnowledgeReferenceValidator validator =
                new OpsAgentModelKnowledgeReferenceValidator(
                        () -> modelRepository,
                        () -> ragRepository,
                        () -> authorization);

        assertDoesNotThrow(() -> validator.validateModel("model-a", "Agent modelId"));
        assertDoesNotThrow(() -> validator.validateKnowledge(
                "knowledge-a", "Agent knowledgeBaseId", "project-a"));
    }

    @Test
    void agentScopePolicyMustRejectDepthAboveOne() {
        OpsAgentScopeDefinitionPolicy policy =
                new OpsAgentScopeDefinitionPolicy(new OpsAgentToolNamePolicy());
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-a")
                .engine("CHAT")
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId("child-a")
                        .instruction("collect evidence")
                        .maxDepth(2)
                        .build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> policy.validate(definition));
    }

    @Test
    void mcpServerPolicyMustRejectRemoteTransportWithoutUrl() {
        OpsAgentMcpServerDefinitionPolicy policy =
                new OpsAgentMcpServerDefinitionPolicy(new OpsAgentToolNamePolicy());

        assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(List.of(OpsMcpServerConfig.builder()
                        .name("remote")
                        .transport("streamable-http")
                        .build()), "Agent"));
    }
}
