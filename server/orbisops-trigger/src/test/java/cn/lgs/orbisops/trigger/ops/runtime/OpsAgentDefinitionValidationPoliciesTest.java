package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
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
        McpClientCatalogPort mcpRepository =
                mock(McpClientCatalogPort.class);
        ProjectMcpAuthorizationApplicationService authorization =
                mock(ProjectMcpAuthorizationApplicationService.class);
        ProjectMcpRuntimeDescriptorApplicationService descriptor =
                mock(ProjectMcpRuntimeDescriptorApplicationService.class);
        OpsSourceRepositoryService sourceRepository =
                mock(OpsSourceRepositoryService.class);
        when(mcpRepository.findByMcpId("source-mcp")).thenReturn(null);
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
    void skillBindingUsesProjectCatalogAndRejectsForeignDisabledOrUnboundGlobalSkills() {
        var catalog = mock(cn.lgs.orbisops.application.skill.SkillCatalogPort.class);
        var validator = new OpsAgentProjectCapabilityReferenceValidator(() -> null, () -> catalog,
                () -> null, () -> null, () -> null, () -> null, () -> null);
        when(catalog.listRuntimeProjectEntries("project-a")).thenReturn(List.of(skill("local", "project-a", "ENABLED"),
                skill("shadowed", "project-a", "DISABLED")));
        when(catalog.listRuntimeProjectEntries("project-b")).thenReturn(List.of());
        when(catalog.listRuntimeGlobalEntries()).thenReturn(List.of(skill("shadowed", "", "ENABLED"), skill("global", "", "ENABLED")));
        when(catalog.configuredGlobalSkillIds("project-a")).thenReturn(List.of("shadowed"));
        assertDoesNotThrow(() -> validator.validateSkills(List.of("local"), "Agent", "project-a"));
        for (String id : List.of("missing", "shadowed", "global")) assertThrows(IllegalArgumentException.class,
                () -> validator.validateSkills(List.of(id), "Agent", "project-a"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateSkills(List.of("local"), "Agent", "project-b"));
        when(catalog.configuredGlobalSkillIds("project-a")).thenReturn(List.of("global"));
        assertDoesNotThrow(() -> validator.validateSkills(List.of("global"), "Agent", "project-a"));
        assertDoesNotThrow(() -> validator.validateSkills(List.of("global"), "Agent", ""));
    }

    private cn.lgs.orbisops.application.skill.SkillCatalogSnapshot skill(String id, String project, String status) {
        java.util.Map<String,Object> view = new java.util.LinkedHashMap<>();
        view.put("skillId", id); view.put("projectId", project); view.put("scope", project.isBlank() ? "GLOBAL" : "PROJECT");
        view.put("version", 1); view.put("skillHash", "hash-" + id); view.put("name", id); view.put("status", status);
        view.put("description", "synthetic method"); view.put("whenToUse", List.of("diagnosis"));
        view.put("whenNotToUse", List.of("production writes"));
        return cn.lgs.orbisops.application.skill.SkillCatalogSnapshot.fromView(view);
    }

    @Test
    void modelKnowledgeValidatorMustUseKnowledgeTagFallback() {
        AiClientModelCatalogPort modelRepository =
                mock(AiClientModelCatalogPort.class);
        RagOrderCatalogPort ragRepository =
                mock(RagOrderCatalogPort.class);
        ProjectKnowledgeAuthorizationApplicationService authorization =
                mock(ProjectKnowledgeAuthorizationApplicationService.class);
        when(modelRepository.findByModelId("model-a")).thenReturn(
                new AiClientModelDefinition(
                        null, "model-a", null, "model-a", null, null, null, 1, null, null));
        when(authorization.allows("project-a", "knowledge-a")).thenReturn(true);
        when(ragRepository.queryByRagId("knowledge-a")).thenReturn(null);
        when(ragRepository.queryByKnowledgeTag("knowledge-a")).thenReturn(List.of(
                new RagOrderDefinition(
                        null, "knowledge-a", "Knowledge A", "knowledge-a", 1, null, null)));
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
