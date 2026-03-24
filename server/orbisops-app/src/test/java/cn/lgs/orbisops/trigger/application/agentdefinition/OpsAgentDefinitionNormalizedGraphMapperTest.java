package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot.OwnerType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentScopeConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsGraphEdge;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpServerConfig;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAgentDefinitionNormalizedGraphMapperTest {

    @Test
    void mapsDefinitionGraphAndAllOwnerBindings() {
        OpsMcpServerConfig agentMcp = OpsMcpServerConfig.builder()
                .name("agent-mcp")
                .transport("http")
                .url("http://agent-mcp")
                .allowedTools(List.of("query"))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("diagnose")
                .type(null)
                .ragEnabled(true)
                .config(Map.of("temperature", 0.2))
                .skills(List.of("node-skill"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("node-mcp")
                        .build()))
                .build();
        OpsGraphEdge edge = OpsGraphEdge.builder()
                .edgeId("start-diagnose")
                .from("start")
                .to("diagnose")
                .conditionType(null)
                .condition(null)
                .dataMapping(Map.of("input", "question"))
                .build();
        OpsAgentScopeConfig scope = OpsAgentScopeConfig.builder()
                .agentId("specialist")
                .maxDepth(null)
                .allowedToolNames(List.of("read-only-query"))
                .skills(List.of("scope-skill"))
                .mcpServers(List.of(OpsMcpServerConfig.builder()
                        .name("scope-mcp")
                        .transport("stdio")
                        .build()))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .skills(List.of("agent-skill", " "))
                .mcpServers(List.of(agentMcp))
                .nodes(List.of(node))
                .edges(List.of(edge))
                .agentscopeAgents(List.of(scope))
                .build();

        AgentDefinitionNormalizedGraphSnapshot snapshot =
                new OpsAgentDefinitionNormalizedGraphMapper().map(definition);

        assertEquals("ops-agent", snapshot.agentId());
        assertEquals("CHAT", snapshot.nodes().get(0).nodeType());
        assertEquals("always", snapshot.edges().get(0).conditionType());
        assertEquals("always", snapshot.edges().get(0).conditionExpression());
        assertEquals(1, snapshot.agentScopes().get(0).maxDepth());
        assertEquals(3, snapshot.skillBindings().size());
        assertEquals(3, snapshot.mcpServerBindings().size());
        assertTrue(snapshot.skillBindings().stream().anyMatch(binding ->
                binding.ownerType() == OwnerType.AGENT && "agent-skill".equals(binding.skillName())));
        assertTrue(snapshot.mcpServerBindings().stream().anyMatch(binding ->
                binding.ownerType() == OwnerType.NODE && "node-mcp".equals(binding.serverName())));
    }
}
