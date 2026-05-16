package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphEdgePromptContextTest {

    @Test
    void shouldRenderActiveIncomingEdgeAndOutgoingChoices() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("prometheus")
                .type("AGENT")
                .agent("prometheus-agent")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(node))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("router")
                                .to("prometheus")
                                .conditionType("route_match")
                                .condition("prometheus")
                                .description("需要指标证据时进入 Prometheus Agent")
                                .dataMapping(Map.of("query", "state.plan.prometheusQuery"))
                                .build(),
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("prometheus")
                                .conditionType("review_decision")
                                .condition("needs:prometheus")
                                .feedback(true)
                                .description("复盘后需要补查指标")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("prometheus")
                                .to("review")
                                .conditionType("always")
                                .condition("always")
                                .description("Prometheus observation 汇入主 Agent 复盘")
                                .build()))
                .build();

        String context = OpsGraphEdgePromptContext.nodePromptContext(definition, node,
                edge -> "prometheus".equals(edge.getCondition()));

        assertTrue(context.contains("active router -> prometheus"));
        assertTrue(context.contains("需要指标证据时进入 Prometheus Agent"));
        assertTrue(context.contains("dataMapping"));
        assertTrue(context.contains("当前节点可选出边"));
        assertTrue(context.contains("当前节点可选出边 JSON"));
        assertTrue(context.contains("\"edge\":\"prometheus->review\""));
        assertTrue(context.contains("\"routeOutputHint\":\"always\""));
        assertTrue(context.contains("Prometheus observation 汇入主 Agent 复盘"));
        assertFalse(context.contains("复盘后需要补查指标"));
    }

    @Test
    void shouldCompactFeedbackHandoffWhenFeedbackEdgeIsActive() {
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("prometheus")
                .type("AGENT")
                .agent("prometheus-agent")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(node))
                .edges(List.of(OpsGraphEdge.builder()
                        .from("review-router")
                        .to("prometheus")
                        .conditionType("review_decision")
                        .condition("needs:prometheus")
                        .feedback(true)
                        .description("复盘后需要补查指标")
                        .build()))
                .build();

        String handoff = OpsGraphEdgePromptContext.compactIncomingHandoff(definition, node,
                edge -> Boolean.TRUE.equals(edge.getFeedback()));

        assertTrue(handoff.contains("review-router->prometheus"));
        assertTrue(handoff.contains("feedback"));
        assertTrue(handoff.contains("复盘后需要补查指标"));
    }

    @Test
    void shouldRenderRouterChoiceJsonForPlannerAndReplanner() {
        OpsWorkflowNode mainRouter = OpsWorkflowNode.builder()
                .nodeId("main-router")
                .type("ROUTER")
                .description("按主 Agent plan 选择数据源")
                .config(Map.of("inputKey", "plan", "routeMode", "multi"))
                .build();
        OpsWorkflowNode reviewRouter = OpsWorkflowNode.builder()
                .nodeId("review-router")
                .type("ROUTER")
                .description("按复盘决策选择是否补查")
                .config(Map.of("inputKey", "review_decision", "routeMode", "multi"))
                .build();
        OpsWorkflowNode prometheus = OpsWorkflowNode.builder()
                .nodeId("prometheus")
                .type("AGENT")
                .agent("prometheus-agent")
                .build();
        OpsWorkflowNode elasticsearch = OpsWorkflowNode.builder()
                .nodeId("elasticsearch")
                .type("AGENT")
                .agent("es-log-agent")
                .build();
        OpsWorkflowNode report = OpsWorkflowNode.builder()
                .nodeId("report")
                .type("REPORT")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(mainRouter, reviewRouter, prometheus, elasticsearch, report))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("main-router")
                                .to("prometheus")
                                .conditionType("route_match")
                                .condition("prometheus")
                                .description("需要指标证据")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("main-router")
                                .to("elasticsearch")
                                .conditionType("route_match")
                                .condition("elasticsearch")
                                .description("需要日志证据")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("prometheus")
                                .conditionType("review_decision")
                                .condition("needs:prometheus")
                                .feedback(true)
                                .description("复盘后补查指标")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("report")
                                .conditionType("default")
                                .condition("final_report")
                                .defaultEdge(true)
                                .description("证据足够，进入报告")
                                .build()))
                .build();

        String plannerChoices = OpsGraphEdgePromptContext.routerChoiceContext(definition,
                "plan",
                edge -> false,
                edge -> Map.of());

        assertTrue(plannerChoices.contains("\"inputKey\":\"plan\""));
        assertTrue(plannerChoices.contains("\"routerNodeId\":\"main-router\""));
        assertTrue(plannerChoices.contains("tasks[].routeKey=prometheus"));
        assertTrue(plannerChoices.contains("tasks[].routeKey=elasticsearch"));
        assertFalse(plannerChoices.contains("\"routerNodeId\":\"review-router\""));

        String replannerChoices = OpsGraphEdgePromptContext.routerChoiceContext(definition,
                "review_decision",
                edge -> "needs:prometheus".equals(edge.getCondition()),
                edge -> Boolean.TRUE.equals(edge.getFeedback())
                        ? Map.<String, Object>of("loopId", "main-loop", "usedRounds", 1, "maxRounds", 3, "remainingRounds", 2, "exhausted", false)
                        : Map.of());

        assertTrue(replannerChoices.contains("\"inputKey\":\"review_decision\""));
        assertTrue(replannerChoices.contains("\"routerNodeId\":\"review-router\""));
        assertTrue(replannerChoices.contains("tasks[].routeKey=prometheus"));
        assertTrue(replannerChoices.contains("\"remainingRounds\":2"));
        assertTrue(replannerChoices.contains("\"exhausted\":false"));
        assertTrue(replannerChoices.contains("\"defaultEdge\":true"));
    }

    @Test
    void shouldRenderDownstreamRouterContractForUpstreamNode() {
        OpsWorkflowNode classifier = OpsWorkflowNode.builder()
                .nodeId("classify")
                .type("AGENT")
                .agent("classifier")
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .description("根据 classify_result 筛选后续节点")
                .config(Map.of("inputKey", "classify_result", "routeMode", "multi"))
                .build();
        OpsWorkflowNode prometheus = OpsWorkflowNode.builder()
                .nodeId("prometheus")
                .type("AGENT")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(classifier, router, prometheus))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("classify")
                                .to("router")
                                .conditionType("always")
                                .condition("always")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("router")
                                .to("prometheus")
                                .conditionType("route_match")
                                .condition("prometheus")
                                .description("需要指标证据")
                                .build()))
                .build();

        String contract = OpsGraphEdgePromptContext.downstreamRouterContract(definition, classifier, edge -> false, edge -> Map.of());

        assertTrue(contract.contains("\"upstreamNodeId\":\"classify\""));
        assertTrue(contract.contains("\"routerNodeId\":\"router\""));
        assertTrue(contract.contains("\"inputKey\":\"classify_result\""));
        assertTrue(contract.contains("\"field\":\"classify_result\""));
        assertTrue(contract.contains("\"condition\":\"prometheus\""));
        assertTrue(contract.contains("不要发明新 key"));
    }
}
