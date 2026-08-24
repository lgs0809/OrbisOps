package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRuntimeResourceContextFactoryTest {

    @Test
    void agentContextMustPreserveDefinitionPolicyAndRequestIdentityPrecedence() {
        OpsRuntimeResourceContextFactory factory = factory();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("definition-project")
                .modelId("definition-model")
                .ragEnabled(true)
                .knowledgeBaseId("definition-kb")
                .changePackageEnabled(true)
                .skills(List.of("definition-skill"))
                .mcpIds(List.of("definition-mcp"))
                .executionTargetIds(List.of("definition-target"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("definition-server")
                        .build()))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .projectId("request-project")
                .modelId("request-model")
                .ragEnabled(false)
                .knowledgeBaseId("request-kb")
                .build();

        OpsRuntimeResourceContext context = factory.agent(
                definition, request, new ArrayList<>(), null);

        assertEquals("request-project", context.getProjectId());
        assertEquals("request-model", context.getModelId());
        assertTrue(context.getRagEnabled());
        assertEquals("definition-kb", context.getKnowledgeBaseId());
        assertTrue(context.getChangePackageEnabled());
        assertEquals(List.of("definition-skill"), List.copyOf(context.getSkillNames()));
        assertEquals(List.of("definition-mcp"), List.copyOf(context.getMcpIds()));
        assertEquals(List.of("definition-target"),
                List.copyOf(context.getExecutionTargetIds()));
        assertEquals("definition-server", context.getMcpServers().get(0).getName());
    }

    @Test
    void nodeContextMustInheritSkillsAndTargetsButNeverAgentMcpResources() {
        OpsRuntimeResourceContextFactory factory = factory();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("agent-1")
                .projectId("project-1")
                .modelId("definition-model")
                .ragEnabled(false)
                .knowledgeBaseId("definition-kb")
                .changePackageEnabled(false)
                .skills(List.of("definition-skill"))
                .mcpIds(List.of("definition-mcp"))
                .executionTargetIds(List.of("definition-target"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("definition-server")
                        .build()))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("node-1")
                .modelId("node-model")
                .ragEnabled(true)
                .knowledgeBaseId("node-kb")
                .repairEnabled(true)
                .changePackageEnabled(true)
                .skills(List.of("node-skill"))
                .mcpIds(List.of("node-mcp"))
                .executionTargetIds(List.of("node-target"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("node-server")
                        .build()))
                .build();

        OpsRuntimeResourceContext context = factory.node(
                definition,
                node,
                OpsAgentChatRequest.builder().modelId("request-model").build(),
                new ArrayList<>(),
                null);

        assertEquals("node-model", context.getModelId());
        assertTrue(context.getRagEnabled());
        assertEquals("node-kb", context.getKnowledgeBaseId());
        assertTrue(context.getRepairEnabled());
        assertTrue(context.getChangePackageEnabled());
        assertEquals(List.of("definition-skill", "node-skill"),
                List.copyOf(context.getSkillNames()));
        assertEquals(List.of("node-mcp"), List.copyOf(context.getMcpIds()));
        assertEquals(List.of("definition-target", "node-target"),
                List.copyOf(context.getExecutionTargetIds()));
        assertEquals(List.of("node-server"), context.getMcpServers().stream()
                .map(OpsMcpServerConfig::getName)
                .toList());
    }

    @Test
    void nodeStringFlagMustInheritOnlyAuthorizedProjectCapabilities() {
        OpsRuntimeSkillResolver skillResolver = mock(OpsRuntimeSkillResolver.class);
        OpsRuntimeMcpResolver mcpResolver = mock(OpsRuntimeMcpResolver.class);
        when(skillResolver.enabledProjectSkillIds("project-1"))
                .thenReturn(List.of("project-skill"));
        when(mcpResolver.enabledProjectMcpIds("project-1"))
                .thenReturn(List.of("project-mcp"));
        OpsRuntimeResourceContextFactory factory =
                new OpsRuntimeResourceContextFactory(skillResolver, mcpResolver);
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("node-1")
                .config(Map.of("inheritProjectCapabilities", "true"))
                .skills(List.of("node-skill"))
                .mcpIds(List.of("node-mcp"))
                .build();

        OpsRuntimeResourceContext context = factory.node(
                OpsAgentDefinition.builder()
                        .agentId("agent-1")
                        .projectId("project-1")
                        .build(),
                node,
                OpsAgentChatRequest.builder().build(),
                new ArrayList<>(),
                null);

        assertEquals(List.of("node-skill", "project-skill"),
                List.copyOf(context.getSkillNames()));
        assertEquals(List.of("node-mcp", "project-mcp"),
                List.copyOf(context.getMcpIds()));
        verify(skillResolver).enabledProjectSkillIds("project-1");
        verify(mcpResolver).enabledProjectMcpIds("project-1");
    }

    @Test
    void agentScopeMustApplyLocalOverridesAndOptionalProjectInheritance() {
        OpsRuntimeSkillResolver skillResolver = mock(OpsRuntimeSkillResolver.class);
        OpsRuntimeMcpResolver mcpResolver = mock(OpsRuntimeMcpResolver.class);
        when(skillResolver.enabledProjectSkillIds("project-1"))
                .thenReturn(List.of("project-skill"));
        when(mcpResolver.enabledProjectMcpIds("project-1"))
                .thenReturn(List.of("project-mcp"));
        OpsRuntimeResourceContextFactory factory =
                new OpsRuntimeResourceContextFactory(skillResolver, mcpResolver);
        OpsAgentScopeConfig scope = OpsAgentScopeConfig.builder()
                .agentId("child-1")
                .modelId("scope-model")
                .ragEnabled(false)
                .knowledgeBaseId("scope-kb")
                .repairEnabled(true)
                .changePackageEnabled(false)
                .inheritProjectCapabilities(true)
                .skills(List.of("scope-skill"))
                .mcpIds(List.of("scope-mcp"))
                .executionTargetIds(List.of("scope-target"))
                .build();

        OpsRuntimeResourceContext context = factory.agentScope(
                OpsAgentDefinition.builder()
                        .agentId("agent-1")
                        .projectId("project-1")
                        .modelId("definition-model")
                        .ragEnabled(true)
                        .knowledgeBaseId("definition-kb")
                        .changePackageEnabled(true)
                        .skills(List.of("definition-skill"))
                        .mcpIds(List.of("definition-mcp"))
                        .executionTargetIds(List.of("definition-target"))
                        .build(),
                scope,
                OpsAgentChatRequest.builder().modelId("request-model").build(),
                new ArrayList<>(),
                null);

        assertEquals("scope-model", context.getModelId());
        assertFalse(context.getRagEnabled());
        assertEquals("scope-kb", context.getKnowledgeBaseId());
        assertTrue(context.getRepairEnabled());
        assertFalse(context.getChangePackageEnabled());
        assertEquals(List.of("definition-skill", "scope-skill", "project-skill"),
                List.copyOf(context.getSkillNames()));
        assertEquals(List.of("scope-mcp", "project-mcp"),
                List.copyOf(context.getMcpIds()));
        assertEquals(List.of("definition-target", "scope-target"),
                List.copyOf(context.getExecutionTargetIds()));
    }

    @Test
    void unboundNodesAndScopesMustUseChatSelectionThenDefinitionDefault() {
        var factory = factory();
        var definition = OpsAgentDefinition.builder().modelId("definition-model").build();
        var node = OpsWorkflowNode.builder().nodeId("unbound").modelId(" ").build();
        var scope = OpsAgentScopeConfig.builder().agentId("unbound-child").build();
        var selected = OpsAgentChatRequest.builder().modelId("request-model").build();
        assertEquals("request-model", factory.node(definition, node, selected, new ArrayList<>(), null).getModelId());
        assertEquals("request-model", factory.agentScope(definition, scope, selected, new ArrayList<>(), null).getModelId());
        assertEquals("definition-model", factory.node(definition, node, null, new ArrayList<>(), null).getModelId());
        assertEquals("definition-model", factory.agentScope(definition, scope, null, new ArrayList<>(), null).getModelId());
    }

    private OpsRuntimeResourceContextFactory factory() {
        return new OpsRuntimeResourceContextFactory(
                mock(OpsRuntimeSkillResolver.class),
                mock(OpsRuntimeMcpResolver.class));
    }
}
