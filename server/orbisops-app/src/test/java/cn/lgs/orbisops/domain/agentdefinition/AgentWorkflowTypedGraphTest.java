package cn.lgs.orbisops.domain.agentdefinition;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentDefinitionNormalizedGraphSnapshot;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentGraphDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowEdgeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeDefinition;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowNodeType;
import cn.lgs.orbisops.domain.agentdefinition.model.AgentWorkflowRouteMode;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentNodeDefinitionPolicy;
import cn.lgs.orbisops.domain.agentdefinition.service.AgentWorkflowNodeTypePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorkflowTypedGraphTest {

    @Test
    void fullLanguageAndLegacyAliasesMustResolveToCanonicalTypes() {
        assertEquals(AgentWorkflowNodeType.LLM,
                AgentWorkflowNodeType.fromPublishedName("chat"));
        assertEquals(AgentWorkflowNodeType.AGENT,
                AgentWorkflowNodeType.fromPublishedName("sub-agent"));
        assertEquals(AgentWorkflowNodeType.TOOL,
                AgentWorkflowNodeType.fromPublishedName("tool_call"));
        assertEquals(AgentWorkflowNodeType.CONDITION,
                AgentWorkflowNodeType.fromPublishedName("router"));
        assertEquals(Set.of(
                        AgentWorkflowNodeType.START,
                        AgentWorkflowNodeType.END,
                        AgentWorkflowNodeType.LLM,
                        AgentWorkflowNodeType.AGENT,
                        AgentWorkflowNodeType.TOOL,
                        AgentWorkflowNodeType.RAG,
                        AgentWorkflowNodeType.SKILL,
                        AgentWorkflowNodeType.CONDITION,
                        AgentWorkflowNodeType.HUMAN_APPROVAL,
                        AgentWorkflowNodeType.PARALLEL,
                        AgentWorkflowNodeType.JOIN,
                        AgentWorkflowNodeType.LOOP,
                        AgentWorkflowNodeType.SUB_WORKFLOW,
                        AgentWorkflowNodeType.WAIT,
                        AgentWorkflowNodeType.COMPENSATION),
                Set.of(AgentWorkflowNodeType.values()));
    }

    @Test
    void knownButDisabledAndUnknownTypesMustFailClosedDifferently() {
        AgentNodeDefinitionPolicy policy = new AgentNodeDefinitionPolicy();
        AgentGraphDefinition.Node parallel = node("parallel", "PARALLEL", "agent-1");
        IllegalArgumentException disabled = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(parallel));
        assertEquals("WORKFLOW_NODE_TYPE_NOT_ENABLED:PARALLEL", disabled.getMessage());

        AgentGraphDefinition.Node unknown = node("unknown", "MAGIC_NODE", "agent-1");
        IllegalArgumentException unsupported = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(unknown));
        assertEquals("WORKFLOW_NODE_TYPE_UNKNOWN:MAGIC_NODE", unsupported.getMessage());
    }

    @Test
    void authoritativeGraphMustExposeTypedNodeAndEdgeProjection() {
        AgentGraphDefinition graph = new AgentGraphDefinition(
                "agent-1",
                "GRAPH",
                "router",
                List.of(
                        node("router", "ROUTER", "router-agent"),
                        node("worker", "CHAT", "worker-agent")),
                List.of(new AgentGraphDefinition.Edge(
                        "router", "worker", "route_match", "worker", false)),
                List.of());

        AgentWorkflowNodeDefinition router = graph.workflowNodes().get(0);
        AgentWorkflowEdgeDefinition edge = graph.workflowEdges().get(0);

        assertEquals(AgentWorkflowNodeType.CONDITION, router.nodeType());
        assertEquals("ROUTER", router.publishedType());
        assertEquals(AgentWorkflowRouteMode.ROUTE_MATCH, edge.routeMode());
        assertEquals("worker", edge.rule().expression());
        assertThrows(UnsupportedOperationException.class,
                () -> router.config().put("x", true));
    }

    @Test
    void normalizedSnapshotMustRestoreSameTypedPublishedLanguage() {
        AgentDefinitionNormalizedGraphSnapshot.Node node =
                new AgentDefinitionNormalizedGraphSnapshot.Node(
                        "worker", "TOOL_CALL", "tool-agent", "REACT", "output",
                        false, "", "tool", "invoke", Map.of("mode", "REACT"), 0);
        AgentDefinitionNormalizedGraphSnapshot.Edge edge =
                new AgentDefinitionNormalizedGraphSnapshot.Edge(
                        "edge-1", "on-success", "worker", "finish",
                        "expression", "#result.success", false, false, 10,
                        Map.of("result", "input"), "route", 0);

        assertEquals(AgentWorkflowNodeType.TOOL, node.workflowDefinition().nodeType());
        assertEquals("TOOL_CALL", node.workflowDefinition().publishedType());
        assertEquals(AgentWorkflowRouteMode.EXPRESSION,
                edge.workflowDefinition().routeMode());
        assertEquals("#result.success", edge.workflowDefinition().rule().expression());
    }

    @Test
    void enabledSubsetMustRemainExplicitAndImmutable() {
        AgentWorkflowNodeTypePolicy policy = new AgentWorkflowNodeTypePolicy();
        assertTrue(policy.enabledTypes().contains(AgentWorkflowNodeType.TOOL));
        assertTrue(policy.enabledTypes().contains(AgentWorkflowNodeType.SUB_WORKFLOW));
        assertFalse(policy.enabledTypes().contains(AgentWorkflowNodeType.PARALLEL));
        assertFalse(policy.enabledTypes().contains(AgentWorkflowNodeType.WAIT));
        assertThrows(UnsupportedOperationException.class,
                () -> policy.enabledTypes().add(AgentWorkflowNodeType.WAIT));
    }

    @Test
    void directMcpActionMustNameRemoteToolAndReferenceNodeBoundMcp() {
        AgentNodeDefinitionPolicy policy = new AgentNodeDefinitionPolicy();
        AgentGraphDefinition.Node valid = new AgentGraphDefinition.Node(
                "direct", "AGENT", "DIRECT", "direct-agent",
                false, false,
                List.of("rabbitmq-mcp"),
                0,
                Map.of("actions", List.of(Map.of(
                        "mcpId", "rabbitmq-mcp",
                        "remoteToolName", "rabbitmq_health",
                        "arguments", Map.of()))));

        policy.validate(valid);

        AgentGraphDefinition.Node missingRemoteTool = new AgentGraphDefinition.Node(
                "direct", "AGENT", "DIRECT", "direct-agent",
                false, false,
                List.of("rabbitmq-mcp"),
                0,
                Map.of("actions", List.of(Map.of("mcpId", "rabbitmq-mcp"))));
        IllegalArgumentException incomplete = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(missingRemoteTool));
        assertTrue(incomplete.getMessage().contains("必须同时配置 mcpId 与 remoteToolName"));

        AgentGraphDefinition.Node unbound = new AgentGraphDefinition.Node(
                "direct", "AGENT", "DIRECT", "direct-agent",
                false, false,
                List.of("other-mcp"),
                0,
                Map.of("actions", List.of(Map.of(
                        "mcpId", "rabbitmq-mcp",
                        "remoteToolName", "rabbitmq_health"))));
        IllegalArgumentException unboundError = assertThrows(
                IllegalArgumentException.class,
                () -> policy.validate(unbound));
        assertTrue(unboundError.getMessage().contains("引用了未绑定 MCP：rabbitmq-mcp"));
    }

    private AgentGraphDefinition.Node node(String nodeId, String type, String agent) {
        return new AgentGraphDefinition.Node(
                nodeId, type, "AUTO", agent, false, false,
                List.of(), 0, Map.of());
    }
}
