package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsRuntimeSubAgentToolBoundaryPolicyTest {

    private final OpsRuntimeSubAgentToolBoundaryPolicy policy =
            new OpsRuntimeSubAgentToolBoundaryPolicy();

    @Test
    void missingSubAgentRoleMustLeaveToolsUntouched() {
        ToolCallback tool = tool("unsafe_admin");
        OpsRuntimeResourceContext context = context(
                OpsAgentScopeConfig.builder().agentId("child").build(),
                List.of(tool));

        policy.enforce(context);

        assertEquals(List.of("unsafe_admin"), toolNames(context));
        assertEquals(false, context.getMetadata().get("authorityContextPresent"));
        assertEquals(1, context.getMetadata().get("authorityFilteredToolCount"));
    }

    @Test
    void depthOtherThanOneMustFailClosed() {
        OpsRuntimeResourceContext context = context(
                OpsAgentScopeConfig.builder()
                        .agentId("child")
                        .role("EVIDENCE_EXPLORER")
                        .maxDepth(2)
                        .allowedToolNames(List.of("project_mcp_*"))
                        .build(),
                List.of(tool("project_mcp_logs")));

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> policy.enforce(context));

        assertTrue(error.getMessage().contains("SUB_AGENT_DEPTH_EXCEEDED"));
    }

    @Test
    void exactAndPrefixAllowlistMustFilterAllOtherTools() {
        ToolCallback projectMcp = tool("project_mcp_logs");
        ToolCallback skill = tool("Skill");
        ToolCallback unsafe = tool("unsafe_admin");
        OpsRuntimeResourceContext context = context(
                OpsAgentScopeConfig.builder()
                        .agentId("child")
                        .role("EVIDENCE_EXPLORER")
                        .maxDepth(1)
                        .allowedToolNames(List.of("project_mcp_*", "Skill"))
                        .build(),
                List.of(projectMcp, skill, unsafe));

        policy.enforce(context);

        assertEquals(List.of("project_mcp_logs", "Skill"), toolNames(context));
        assertEquals("EVIDENCE_EXPLORER", context.getMetadata().get("subAgentRole"));
        assertEquals(1, context.getMetadata().get("subAgentMaxDepth"));
        assertEquals(List.of("project_mcp_*", "Skill"),
                context.getMetadata().get("subAgentAllowedTools"));
        assertEquals(2, context.getMetadata().get("subAgentToolCount"));
    }

    @Test
    void absentExplicitAllowlistMustUseBuiltinRoleDefaults() {
        ToolCallback projectMcp = tool("project_mcp_logs");
        ToolCallback unsafe = tool("unsafe_admin");
        OpsRuntimeResourceContext context = context(
                OpsAgentScopeConfig.builder()
                        .agentId("child")
                        .role("EVIDENCE_EXPLORER")
                        .maxDepth(1)
                        .build(),
                List.of(projectMcp, unsafe));

        policy.enforce(context);

        assertEquals(List.of("project_mcp_logs"), toolNames(context));
        assertTrue(((List<?>) context.getMetadata().get("subAgentAllowedTools"))
                .stream()
                .anyMatch(pattern -> String.valueOf(pattern).startsWith("project_mcp_")));
    }

    private OpsRuntimeResourceContext context(
            OpsAgentScopeConfig scope,
            List<ToolCallback> tools) {
        return OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("agent-1").build())
                .agentScope(scope)
                .request(OpsAgentChatRequest.builder().projectId("project-1").build())
                .projectId("project-1")
                .tools(new ArrayList<>(tools))
                .build();
    }

    private ToolCallback tool(String name) {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        when(definition.description()).thenReturn(name + " test tool");
        when(callback.getToolDefinition()).thenReturn(definition);
        return OpsRuntimeGovernedToolCallback.wrap(
                callback,
                OpsRuntimeToolAuthorityDescriptor.readOnly(
                        "TEST",
                        Set.of(
                                AgentExecutionStage.INVESTIGATE,
                                AgentExecutionStage.PREPARE),
                        Set.of(name)));
    }

    private List<String> toolNames(OpsRuntimeResourceContext context) {
        return context.getTools().stream()
                .map(ToolCallback::getToolDefinition)
                .map(ToolDefinition::name)
                .toList();
    }
}
