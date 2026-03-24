package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.testsupport.OpsAgentDefinitionValidatorTestFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAgentDefinitionValidatorTest {

    private final OpsAgentDefinitionValidator validator =
            OpsAgentDefinitionValidatorTestFactory.create();

    @Test
    void shouldRejectDuplicateNodeId() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("start", "AGENT", "ops-agent")))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectEdgePointingToMissingNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(node("start", "START", "start")))
                .edges(List.of(OpsGraphEdge.builder().from("start").to("missing").condition("always").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectLegacyLoopFeedbackConditionType() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("router", "ROUTER", "ops-router"),
                        node("agent", "AGENT", "ops-agent")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("router").to("agent").conditionType("loop_feedback").condition("needs:agent").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectInvalidMcpConfig() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(OpsWorkflowNode.builder()
                        .nodeId("start")
                        .type("MCP")
                        .agent("tool-agent")
                        .mcpServers(List.of(OpsMcpServerConfig.builder().name("bad").transport("sse").build()))
                        .build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAcceptSemanticStartAgentEndGraph() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("HYBRID")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("agent", "AGENT", "ops-agent"),
                        node("end", "END", "end")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("agent").condition("always").build(),
                        OpsGraphEdge.builder().from("agent").to("end").condition("always").build()))
                .build();

        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldAcceptTypedRouteEdgesAndLoopPolicy() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("main", "AGENT", "ops-main-agent"),
                        node("router", "ROUTER", "ops-router"),
                        node("elk", "AGENT", "es-log-agent", "elasticsearch"),
                        node("review", "REVIEW", "ops-main-agent"),
                        node("review-router", "ROUTER", "ops-review-router"),
                        node("report", "REPORT", "ops-final-report"),
                        node("end", "END", "end")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("main").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("main").to("router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("router").to("elk").conditionType("route_match").condition("elasticsearch").build(),
                        OpsGraphEdge.builder().from("elk").to("review").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("review").to("review-router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("review-router").to("elk").conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build(),
                        OpsGraphEdge.builder().from("review-router").to("report").conditionType("default").condition("default").build(),
                        OpsGraphEdge.builder().from("report").to("end").conditionType("default").condition("default").defaultEdge(true).build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .nodes(List.of("main", "router", "elk", "review", "review-router"))
                        .feedbackEdges(List.of("review-router->elk"))
                        .maxRounds(3)
                        .stopCondition("evidence_sufficient || round_limit")
                        .build()))
                .build();

        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectLoopFeedbackEdgePointingToMissingEdge() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("review-router", "ROUTER", "ops-review-router"),
                        node("elk", "AGENT", "es-log-agent", "elasticsearch"),
                        node("report", "REPORT", "report")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("review-router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("review-router").to("report").conditionType("default").condition("default").build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .feedbackEdges(List.of("review-router->elk"))
                        .exitEdge("review-router->report")
                        .maxRounds(2)
                        .build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectLoopExitEdgePointingToMissingEdge() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("review-router", "ROUTER", "ops-review-router"),
                        node("elk", "AGENT", "es-log-agent", "elasticsearch"),
                        node("report", "REPORT", "report")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("review-router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("review-router").to("elk").conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .feedbackEdges(List.of("review-router->elk"))
                        .exitEdge("review-router->report")
                        .maxRounds(2)
                        .build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectRouteEdgeFromNonRouterNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("main", "AGENT", "ops-main-agent"),
                        node("elk", "AGENT", "es-log-agent")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("main").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("main").to("elk").conditionType("route_match").condition("elasticsearch").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectReviewDecisionEdgeFromNonRouterNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("review", "AGENT", "ops-main-agent"),
                        node("elk", "AGENT", "es-log-agent")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("review").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("review").to("elk").conditionType("review_decision").condition("needs:elasticsearch").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectReactAgentWithoutMcpOrRag() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        OpsWorkflowNode.builder()
                                .nodeId("collector")
                                .type("AGENT")
                                .mode("react")
                                .agent("collector-agent")
                                .build()))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("collector").conditionType("always").condition("always").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldAllowRouteKeyIndependentOfNodeIdentity() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("router", "ROUTER", "ops-router"),
                        node("prom", "AGENT", "prometheus-agent", "prometheus"),
                        node("end", "END", "end")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("router").conditionType("always").condition("always").build(),
                        OpsGraphEdge.builder().from("router").to("prom").conditionType("route_match").condition("elasticsearch").build(),
                        OpsGraphEdge.builder().from("router").to("end").conditionType("default").condition("default").build(),
                        OpsGraphEdge.builder().from("prom").to("end").conditionType("always").condition("always").build()))
                .build();

        assertDoesNotThrow(() -> validator.validate(definition));
    }

    @Test
    void shouldRejectGraphWithoutExplicitEndNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("agent", "AGENT", "ops-agent")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("agent").condition("always").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectNodeThatCannotConvergeToEnd() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        node("start", "START", "start"),
                        node("router", "ROUTER", "ops-router"),
                        node("loop-a", "AGENT", "loop-a-agent"),
                        node("loop-b", "AGENT", "loop-b-agent"),
                        node("end", "END", "end")))
                .edges(List.of(
                        OpsGraphEdge.builder().from("start").to("router").condition("always").build(),
                        OpsGraphEdge.builder().from("router").to("end").conditionType("default").condition("default").build(),
                        OpsGraphEdge.builder().from("router").to("loop-a").conditionType("route_match").condition("loop").build(),
                        OpsGraphEdge.builder().from("loop-a").to("loop-b").condition("always").build(),
                        OpsGraphEdge.builder().from("loop-b").to("loop-a").condition("always").build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition));
    }

    @Test
    void shouldRejectMissingMcpReferenceWhenRegistryAvailable() {
        McpClientCatalogPort mcpDao = mock(McpClientCatalogPort.class);
        when(mcpDao.findByMcpId("missing-mcp")).thenReturn(null);
        OpsAgentDefinitionValidator validatorWithMcpRepository =
                OpsAgentDefinitionValidatorTestFactory.create(mcpDao);

        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("CHAT")
                .mcpIds(List.of("missing-mcp"))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> validatorWithMcpRepository.validate(definition));
    }

    @Test
    void shouldRejectChangePackageToolOnStructureNode() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .startNodeId("start")
                .nodes(List.of(
                        OpsWorkflowNode.builder()
                                .nodeId("start")
                                .type("START")
                                .agent("start")
                                .changePackageEnabled(true)
                                .build(),
                        node("end", "END", "end")))
                .edges(List.of(OpsGraphEdge.builder()
                        .from("start")
                        .to("end")
                        .condition("always")
                        .build()))
                .build();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(definition));
        assertTrue(error.getMessage().contains("结构节点"));
    }

    private OpsWorkflowNode node(String nodeId, String type, String agent) {
        return OpsWorkflowNode.builder()
                .nodeId(nodeId)
                .type(type)
                .agent(agent)
                .build();
    }

    private OpsWorkflowNode node(String nodeId, String type, String agent, String routeKey) {
        return OpsWorkflowNode.builder()
                .nodeId(nodeId)
                .type(type)
                .agent(agent)
                .build();
    }

}
