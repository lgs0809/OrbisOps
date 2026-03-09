package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsAgentCapabilityBindingEditorTest {

    private final OpsAgentCapabilityBindingEditor editor = new OpsAgentCapabilityBindingEditor();

    @Test
    void shouldApplyTypedBindingsToAgentNodeAndAgentScope() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .skills(List.of("old-skill"))
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("mysql-agent")
                        .mcpIds(List.of("old-mcp"))
                        .build()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder()
                        .agentId("repair-agent")
                        .executionTargetIds(List.of("old-target"))
                        .build()))
                .build();

        editor.applyBindings(definition, List.of(
                Map.of("ownerType", "AGENT", "capabilityType", "skill", "capabilityId", "demo-project-sop"),
                Map.of("ownerType", "NODE", "nodeId", "mysql-agent",
                        "capabilityType", "project_tool", "capabilityId", "demo-project-mysql-mcp"),
                Map.of("ownerType", "AGENTSCOPE", "nodeId", "repair-agent",
                        "capabilityType", "execution_target", "capabilityId", "demo-project-runtime")));

        assertEquals(List.of("demo-project-sop"), definition.getSkills());
        assertEquals(List.of("demo-project-mysql-mcp"), definition.getNodes().get(0).getMcpIds());
        assertEquals(List.of("demo-project-runtime"),
                definition.getAgentscopeAgents().get(0).getExecutionTargetIds());
    }

    @Test
    void shouldRejectUnknownOwnerAndInlineMcpBinding() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("demo-project-agent")
                .projectId("demo-project")
                .nodes(List.of(OpsWorkflowNode.builder().nodeId("mysql-agent").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> editor.applyBindings(definition, List.of(
                Map.of("ownerType", "NODE", "nodeId", "missing-node",
                        "capabilityType", "skill", "capabilityId", "demo-project-sop"))));
        assertThrows(IllegalArgumentException.class, () -> editor.applyBindings(definition, List.of(
                Map.of("ownerType", "AGENT", "capabilityType", "inline_mcp_server",
                        "capabilityId", "raw-mcp"))));
    }

    @Test
    void shouldReadOnlyMapEntriesFromCompatibilityRequest() {
        List<Map<String, Object>> bindings = editor.requestBindings(Map.of("bindings", List.of(
                Map.of("ownerType", "AGENT", "capabilityType", "skill", "capabilityId", "demo-project-sop"),
                "ignored")));

        assertEquals(1, bindings.size());
        assertEquals("demo-project-sop", bindings.get(0).get("capabilityId"));
    }
}
