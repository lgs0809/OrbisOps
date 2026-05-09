package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphFeedbackLoopRuntimeTest {

    @Test
    void progressiveMcpAgentHasEnoughBoundedRoundsForARealRemoteCall() {
        OpsAgentScopeExecutor executor = agentScopeExecutor();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .defaultSubAgentMaxIterations(3)
                .build();
        OpsAgentScopeConfig mcpAgent = OpsAgentScopeConfig.builder()
                .mcpIds(List.of("project-elasticsearch-readonly"))
                .build();
        OpsAgentScopeConfig localAgent = OpsAgentScopeConfig.builder().build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .metadata(new java.util.HashMap<>())
                .build();

        assertEquals(6, executor.maxToolRounds(definition, mcpAgent, request));
        assertEquals(3, executor.maxToolRounds(definition, localAgent, request));
    }

    @Test
    void legacySubAgentBudgetMustNotCapProjectDefaultMainReactAgent() {
        OpsAgentScopeExecutor executor = agentScopeExecutor();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .defaultSubAgentMaxIterations(8)
                .build();
        OpsAgentRunRequestDTO analysisRequest = new OpsAgentRunRequestDTO();
        analysisRequest.setSubAgentMaxIterations(2);
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, analysisRequest);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .metadata(metadata)
                .build();

        assertEquals(8, executor.maxToolRounds(
                definition,
                OpsAgentScopeConfig.builder().role("general").build(),
                request));
        assertEquals(2, executor.maxToolRounds(
                definition,
                OpsAgentScopeConfig.builder().role("evidence_explorer").build(),
                request));
    }

    @Test
    void shouldComposeGlobalAndNodeSystemPrompt() {
        OpsRuntimePromptAssembler promptAssembler = new OpsRuntimePromptAssembler();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .instruction("全局约束：按证据回答。")
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .instruction("节点约束：只分析 Prometheus 指标。")
                .build();
        OpsRuntimeResourceBundle bundle = OpsRuntimeResourceBundle.builder()
                .skillContext("Skill 摘要：先看 5xx 指标。")
                .build();

        String prompt = promptAssembler.systemPrompt(definition, node, bundle);

        assertTrue(prompt.contains("### 全局 System Prompt\n全局约束：按证据回答。"));
        assertTrue(prompt.contains("### 节点 System Prompt\n节点约束：只分析 Prometheus 指标。"));
        assertTrue(prompt.contains("### 可用 Skill 与使用边界\nSkill 摘要：先看 5xx 指标。"));
    }

    @Test
    void shouldComposeGlobalAndAgentScopeSystemPromptWithoutDuplicatingSameText() {
        OpsRuntimePromptAssembler promptAssembler = new OpsRuntimePromptAssembler();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .instruction("全局约束：不要执行变更。")
                .build();
        OpsAgentScopeConfig config = OpsAgentScopeConfig.builder()
                .instruction("子 Agent 约束：只读查询 ELK。")
                .build();

        String prompt = promptAssembler.agentInstruction(definition, config, null);

        assertTrue(prompt.contains("### 全局 System Prompt\n全局约束：不要执行变更。"));
        assertTrue(prompt.contains("### 子 Agent System Prompt\n子 Agent 约束：只读查询 ELK。"));

        OpsAgentScopeConfig duplicate = OpsAgentScopeConfig.builder()
                .instruction("全局约束：不要执行变更。")
                .build();
        String deduped = promptAssembler.agentInstruction(definition, duplicate, null);
        assertEquals(1, deduped.split("全局约束：不要执行变更。", -1).length - 1);
        assertTrue(deduped.contains("### ReAct 证据与最终结果协议"));
    }

    @Test
    void shouldAllowNodeToOptOutOfGlobalSystemPrompt() {
        OpsRuntimePromptAssembler promptAssembler = new OpsRuntimePromptAssembler();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .instruction("全局约束：输出完整诊断报告。")
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .instruction("节点约束：只返回 Prometheus 原始 observation。")
                .config(Map.of("globalPromptMode", "node_only"))
                .build();

        String prompt = promptAssembler.systemPrompt(definition, node, null);

        assertFalse(prompt.contains("全局约束：输出完整诊断报告。"));
        assertTrue(prompt.startsWith("节点约束：只返回 Prometheus 原始 observation。\n\n"));
        assertTrue(prompt.contains("### 本轮服务器时间基准"));
        assertEquals(1, prompt.split("currentTimeUtc:", -1).length - 1);
        assertFalse(prompt.contains("### 全局 System Prompt"));
        assertFalse(prompt.contains("### ReAct 证据与最终结果协议"));
    }

    @Test
    void shouldStopFeedbackEdgeAfterLoopLimitIsReached() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .defaultMaxMainRounds(3)
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("rag")
                                .conditionType("review_decision")
                                .condition("needs:rag")
                                .feedback(true)
                                .build(),
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("report")
                                .conditionType("default")
                                .condition("default")
                                .build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .feedbackEdges(List.of("review-router->rag"))
                        .exitEdge("review-router->report")
                        .maxRounds(2)
                        .build()))
                .build();
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        OverAllState state = new OverAllState(Map.of("review_decision", "needs:rag"));
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("review-router")
                .type("ROUTER")
                .build();
        OpsGraphEdge feedbackEdge = OpsGraphEdge.builder()
                .from("review-router")
                .to("rag")
                .conditionType("review_decision")
                .condition("needs:rag")
                .feedback(true)
                .build();

        assertEquals(List.of("needs:rag"), route( definition, request, state, router, feedbackEdge));
        assertEquals(1, loopRound(state));
        assertEquals(List.of("needs:rag"), route( definition, request, state, router, feedbackEdge));
        assertEquals(2, loopRound(state));

        List<String> blocked = route( definition, request, state, router, feedbackEdge);
        assertEquals(List.of("__loop_exit__:investigation"), blocked);
        assertEquals(2, loopRound(state));
    }

    @Test
    void shouldApplyDefaultLoopLimitForFeedbackEdgeWithoutLoopPolicy() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .defaultMaxMainRounds(1)
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("rag")
                                .conditionType("review_decision")
                                .condition("needs:rag")
                                .feedback(true)
                                .build()))
                .build();
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        OverAllState state = new OverAllState(Map.of("review_decision", "needs:rag"));
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("review-router")
                .type("ROUTER")
                .build();
        OpsGraphEdge feedbackEdge = definition.getEdges().get(0);

        assertEquals(List.of("needs:rag"), route( definition, request, state, router, feedbackEdge));
        assertEquals(1, loopRound(state, "review-router->rag"));
        assertEquals(List.of("__default__"), route( definition, request, state, router, feedbackEdge));
        assertEquals(1, loopRound(state, "review-router->rag"));
    }

    @Test
    void shouldSelectMultipleFeedbackEdgesAndCountSameLoopOncePerRouterDecision() {
        OpsGraphEdge esEdge = OpsGraphEdge.builder()
                .from("review-router")
                .to("es")
                .conditionType("review_decision")
                .condition("needs:elasticsearch")
                .feedback(true)
                .build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder()
                .from("review-router")
                .to("prometheus")
                .conditionType("review_decision")
                .condition("needs:prometheus")
                .feedback(true)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .defaultMaxMainRounds(3)
                .edges(List.of(
                        esEdge,
                        prometheusEdge,
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("report")
                                .conditionType("default")
                                .condition("default")
                                .build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .feedbackEdges(List.of("review-router->es", "review-router->prometheus"))
                        .exitEdge("review-router->report")
                        .maxRounds(2)
                        .build()))
                .build();
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        OverAllState state = new OverAllState(Map.of("review_decision", "needs:elasticsearch,needs:prometheus"));
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("review-router")
                .type("ROUTER")
                .config(Map.of("routeMode", "multi"))
                .build();

        assertEquals(List.of("needs:elasticsearch", "needs:prometheus"),
                route( definition, request, state, router, List.of(esEdge, prometheusEdge), true));
        assertEquals(1, loopRound(state));

        assertEquals(List.of("needs:elasticsearch", "needs:prometheus"),
                route( definition, request, state, router, List.of(esEdge, prometheusEdge), true));
        assertEquals(2, loopRound(state));

        assertEquals(List.of("__loop_exit__:investigation"),
                route( definition, request, state, router, List.of(esEdge, prometheusEdge), true));
        assertEquals(2, loopRound(state));
    }

    @Test
    void shouldMatchRouteConditionFromRouterTextOutput() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .edges(List.of())
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder()
                .from("router")
                .to("prometheus")
                .conditionType("route_match")
                .condition("prometheus")
                .build();
        OverAllState outputState = new OverAllState(Map.of("output", "{\"tasks\":[{\"source\":\"prometheus\"}]}"));
        OverAllState selectedRoutesState = new OverAllState(Map.of("selectedRoutes", "needs:prometheus"));

        assertEquals(List.of("prometheus"), route( definition, new OpsAgentRunRequestDTO(), outputState, router, prometheusEdge));
        assertEquals(List.of("prometheus"), route( definition, new OpsAgentRunRequestDTO(), selectedRoutesState, router, prometheusEdge));
    }

    @Test
    void explicitRouterSelectionDoesNotFallBackToBroaderPlanCandidates() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops").edges(List.of()).build();
        OpsWorkflowNode router = OpsWorkflowNode.builder().nodeId("router").type("ROUTER")
                .config(Map.of("routeMode", "multi")).build();
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("router").to("elasticsearch")
                .conditionType("route_match").condition("elasticsearch").build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder().from("router").to("prometheus")
                .conditionType("route_match").condition("prometheus").build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO broaderPlan = OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .tasks(List.of(
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("elasticsearch").build(),
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("prometheus").build()))
                .build();
        OverAllState state = new OverAllState(Map.of(
                "selectedRoutes", List.of("elasticsearch"),
                "plan", broaderPlan,
                "output", "Router 已选择分支：elasticsearch"));

        assertEquals(List.of("elasticsearch"), route( definition, new OpsAgentRunRequestDTO(), state,
                router, List.of(elasticsearchEdge, prometheusEdge), true));
    }

    @Test
    void authoritativeIntentConstraintsOverrideGenericRouterModelOutput() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops").edges(List.of()).build();
        OpsWorkflowNode router = OpsWorkflowNode.builder().nodeId("router").type("ROUTER")
                .config(Map.of("routeMode", "multi")).build();
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("router").to("elasticsearch")
                .conditionType("route_match").condition("elasticsearch").build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder().from("router").to("prometheus")
                .conditionType("route_match").condition("prometheus").build();
        OverAllState state = new OverAllState(Map.of(
                "output", "{\"tasks\":[{\"source\":\"elasticsearch\"},{\"source\":\"prometheus\"}]}",
                "intentAllowedRoutes", List.of("elasticsearch"),
                "intentExcludedRoutes", List.of("prometheus", "rag")));

        assertEquals(List.of("elasticsearch"), route( definition, new OpsAgentRunRequestDTO(), state,
                router, List.of(elasticsearchEdge, prometheusEdge), true));
    }

    @Test
    void authoritativeIntentConstraintsAlsoApplyToReviewFeedbackRoutes() {
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("review-router").to("elasticsearch")
                .conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder().from("review-router").to("prometheus")
                .conditionType("review_decision").condition("needs:prometheus").feedback(true).build();
        OpsGraphEdge ragEdge = OpsGraphEdge.builder().from("review-router").to("rag")
                .conditionType("review_decision").condition("needs:rag").feedback(true).build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops")
                .defaultMaxMainRounds(2)
                .edges(List.of(elasticsearchEdge, prometheusEdge, ragEdge))
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER")
                .config(Map.of("routeMode", "multi")).build();
        OverAllState state = new OverAllState(Map.of(
                "output", "needs:elasticsearch,needs:prometheus,needs:rag",
                "intentAllowedRoutes", List.of("elasticsearch"),
                "intentExcludedRoutes", List.of("prometheus", "rag")));

        assertEquals(List.of("needs:elasticsearch"), route( definition, new OpsAgentRunRequestDTO(), state,
                router, List.of(elasticsearchEdge, prometheusEdge, ragEdge), true));
    }

    @Test
    void authoritativeIntentConstraintsSurviveWhenGraphStateDropsConstraintKeys() {
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("review-router").to("elasticsearch")
                .conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder().from("review-router").to("prometheus")
                .conditionType("review_decision").condition("needs:prometheus").feedback(true).build();
        OpsGraphEdge ragEdge = OpsGraphEdge.builder().from("review-router").to("rag")
                .conditionType("review_decision").condition("needs:rag").feedback(true).build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops")
                .defaultMaxMainRounds(2)
                .edges(List.of(elasticsearchEdge, prometheusEdge, ragEdge))
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER")
                .config(Map.of("routeMode", "multi")).build();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        metadata.put("excludedCapabilities", List.of("PROMETHEUS", "RAG"));
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder().metadata(metadata).build();
        OverAllState stateWithoutConstraints = new OverAllState(Map.of(
                "output", "needs:elasticsearch,needs:prometheus,needs:rag"));

        assertEquals(List.of("needs:elasticsearch"), topologyAssembler().routeConditions(
                definition, new OpsAgentRunRequestDTO(), stateWithoutConstraints,
                List.of(elasticsearchEdge, prometheusEdge, ragEdge), router, "end", true, runtimeRequest));
    }

    @Test
    void singleExplicitSourceCannotBeScheduledAgainThroughAnyFeedbackRouter() {
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("review-router").to("elasticsearch")
                .conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops")
                .defaultMaxMainRounds(3)
                .edges(List.of(elasticsearchEdge))
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER")
                .config(Map.of("routeMode", "multi")).build();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder().metadata(metadata).build();
        OverAllState state = new OverAllState(Map.of(
                "output", "needs:elasticsearch",
                "review_decision", "needs:elasticsearch",
                "selectedReviewRoutes", List.of("elasticsearch"),
                "investigationExecuted", true,
                "results", List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .status("SUCCEEDED")
                        .summary("真实查询完成，命中 0 条。")
                        .build())));

        assertEquals(List.of("__default__"), topologyAssembler().routeConditions(
                definition, new OpsAgentRunRequestDTO(), state, List.of(elasticsearchEdge), router,
                "end", true, runtimeRequest));
    }

    @Test
    void serializedIntentDecisionCannotControlGraphRuntime() {
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("review-router").to("elasticsearch")
                .conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("intentDecision", Map.of(
                "slots", Map.of("allowedInvestigationSources", List.of("ELASTICSEARCH"))));
        OpsAgentChatRequest serializedRequest = OpsAgentChatRequest.builder().metadata(metadata).build();
        OverAllState state = new OverAllState(Map.of(
                "output", "needs:elasticsearch",
                "investigationExecuted", true));

        assertEquals(List.of("needs:elasticsearch"), topologyAssembler().routeConditions(
                OpsAgentDefinition.builder().agentId("ops").edges(List.of(elasticsearchEdge)).build(),
                new OpsAgentRunRequestDTO(), state, List.of(elasticsearchEdge),
                OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER").build(),
                "end", true, serializedRequest));
    }

    @Test
    void explicitAnalysisRouteConstraintsSurviveWithoutDecisionObjects() {
        OpsGraphEdge elasticsearchEdge = OpsGraphEdge.builder().from("review-router").to("elasticsearch")
                .conditionType("review_decision").condition("needs:elasticsearch").feedback(true).build();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        metadata.put("excludedCapabilities", List.of("PROMETHEUS", "RAG"));
        metadata.put("_runtimeInvestigationExecuted", true);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder().metadata(metadata).build();
        OverAllState state = new OverAllState(Map.of(
                "output", "needs:elasticsearch"));

        assertEquals(List.of("__default__"), topologyAssembler().routeConditions(
                OpsAgentDefinition.builder().agentId("ops").edges(List.of(elasticsearchEdge)).build(),
                new OpsAgentRunRequestDTO(), state, List.of(elasticsearchEdge),
                OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER").build(),
                "end", true, request));
    }

    @Test
    void analysisRuntimeStateSurvivesMetadataLossWithinSameCanonicalRun() {
        OpsAnalysisRuntimeStateManager stateManager =
                OpsAnalysisRuntimeStateManagerTestFactory.create();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, new OpsAgentRunRequestDTO());
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY, new OpsAnalysisResponseDTO());
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-analysis-state")
                .query("只查询 Elasticsearch")
                .metadata(metadata)
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder().agentId("ops").version(1).build();

        Object first = stateManager.ensure(definition, request);
        metadata.remove("_opsAnalysisState");
        Object recovered = stateManager.ensure(definition, request);

        assertSame(first, recovered);
    }

    @Test
    void explicitSingleSourceReviewCanConvergeOnlyAfterRealExecutionResultExists() {
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-single-source")
                .metadata(metadata)
                .build();
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> realResults = List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .status("SUCCEEDED")
                        .summary("真实查询完成，命中 0 条。")
                        .build());
        java.util.HashMap<String, Object> agentScopeResult = new java.util.HashMap<>();
        agentScopeResult.put("latestObservation", realResults.get(0));
        stateManager.markAgentScopeInvestigationCompleted(request, agentScopeResult, routingPolicy);

        assertEquals(true, agentScopeResult.get("investigationExecuted"));
        assertTrue(stateManager.completedExplicitSingleSourceInvestigation(request, realResults, routingPolicy));
        assertFalse(stateManager.completedExplicitSingleSourceInvestigation(request, List.of(), routingPolicy));
        assertTrue(stateManager.completedExplicitSingleSourceGraphInvestigation(
                request, new OverAllState(Map.of("investigationExecuted", true)), routingPolicy));
    }

    @Test
    void reviewUsesAgentScopeObservationFromGraphStateWhenServiceSnapshotIsEmpty() {
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .status("FOUND")
                        .summary("真实 Elasticsearch 查询已完成。")
                        .build();
        OverAllState graphState = new OverAllState(Map.of("results", List.of(observation)));

        List<OpsAnalysisResponseDTO.InvestigationResultDTO> snapshot = stateManager.analysisResultSnapshot(
                new java.util.ArrayList<>(), graphState, routingPolicy);

        assertEquals(1, snapshot.size());
        assertSame(observation, snapshot.get(0));
    }

    @Test
    void genericGraphReviewConvergesOnlyWhenTheExplicitSourceHasRealObservation() {
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-generic-single-source")
                .metadata(new java.util.HashMap<>(Map.of(
                        "allowedInvestigationSources", List.of("ELASTICSEARCH"))))
                .build();
        OverAllState completed = new OverAllState(Map.of(
                "investigationExecuted", true,
                "results", List.of(Map.of("source", "elasticsearch", "summary", "真实查询完成。"))));
        OverAllState wrongSource = new OverAllState(Map.of(
                "investigationExecuted", true,
                "results", List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .summary("非用户指定来源。")
                        .build())));

        assertTrue(stateManager.completedExplicitSingleSourceGraphInvestigation(
                request, completed, routingPolicy));
        assertFalse(stateManager.completedExplicitSingleSourceGraphInvestigation(
                request, wrongSource, routingPolicy));
        assertFalse(stateManager.completedExplicitSingleSourceGraphInvestigation(
                request, new OverAllState(Map.of()), routingPolicy));
    }

    @Test
    void reviewRouterSkipsModelOnlyForFinalReportDecision() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();

        assertTrue(coordinator.graphReviewDecisionIsFinalReport(
                new OverAllState(Map.of("review_decision", "final_report"))));
        assertFalse(coordinator.graphReviewDecisionIsFinalReport(
                new OverAllState(Map.of("review_decision", "needs:elasticsearch"))));
    }

    @Test
    void genericAgentScopeNodeAlwaysCreatesRoutableObservationWithoutAnalysisState() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .edges(List.of(OpsGraphEdge.builder()
                        .from("router")
                        .to("es-investigation")
                        .condition("elasticsearch")
                        .build()))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("es-investigation")
                .type("AGENT")
                .agent("es-log-agent")
                .subEngine("AGENTSCOPE")
                .build();

        Map<String, Object> result = coordinator.createAgentScopeObservationResult(
                definition, node, "查询完成，命中 0 条。");
        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                (OpsAnalysisResponseDTO.InvestigationResultDTO) result.get("latestObservation");

        assertEquals("elasticsearch", observation.getSource());
        assertEquals(1, ((List<?>) result.get("results")).size());
    }

    @Test
    void replanCannotReintroduceSourcesExcludedByAuthoritativeIntent() {
        OpsAnalysisNodeExecutionCoordinator coordinator = analysisNodeExecutionCoordinator();
        java.util.HashMap<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        metadata.put("excludedCapabilities", List.of("PROMETHEUS", "RAG", "MYSQL_SLOW_SQL"));
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder().metadata(metadata).build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO proposed = OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .tasks(List.of(
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("elasticsearch").priority(1).build(),
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("prometheus").priority(2).build(),
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("rag").priority(3).build(),
                        OpsAnalysisResponseDTO.InvestigationTaskDTO.builder().source("mysql_slow_sql").priority(4).build()))
                .build();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filtered = coordinator.filterGraphReplanTasks(
                proposed, List.of(), OpsAgentDefinition.builder().build(),
                new OpsAgentRunRequestDTO(), runtimeRequest, new OverAllState(Map.of()));

        assertEquals(List.of("elasticsearch"), filtered.getTasks().stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource).toList());

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO afterRealResult = coordinator.filterGraphReplanTasks(
                proposed, List.of(
                        OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                                .source("elasticsearch").status("SUCCEEDED").summary("真实查询完成，命中 0 条。")
                                .build()), OpsAgentDefinition.builder().build(), new OpsAgentRunRequestDTO(),
                runtimeRequest, new OverAllState(Map.of()));
        assertTrue(afterRealResult.getTasks().isEmpty());
    }

    @Test
    void shouldMatchCustomRouteConditionFromRouterTextOutput() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .edges(List.of())
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .build();
        OpsGraphEdge customEdge = OpsGraphEdge.builder()
                .from("router")
                .to("custom")
                .conditionType("route_match")
                .condition("inventory_agent")
                .build();
        OverAllState state = new OverAllState(Map.of("output", "{\"next\":\"inventory_agent\"}"));

        assertEquals(List.of("inventory_agent"), route( definition, new OpsAgentRunRequestDTO(), state, router, customEdge));
    }

    @Test
    void shouldNotMatchRouteConditionFromUnrelatedText() {
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .edges(List.of())
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .build();
        OpsGraphEdge prometheusEdge = OpsGraphEdge.builder()
                .from("router")
                .to("prometheus")
                .conditionType("route_match")
                .condition("prometheus")
                .build();
        OverAllState state = new OverAllState(Map.of("output", "证据足够，直接出报告"));

        assertEquals(List.of("__default__"), route( definition, new OpsAgentRunRequestDTO(), state, router, prometheusEdge));
    }

    @Test
    void shouldAllowExecutedSourceWhenFeedbackEdgeStillHasRemainingRounds() {
        OpsAnalysisNodeExecutionCoordinator coordinator = analysisNodeExecutionCoordinator();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(
                        OpsWorkflowNode.builder().nodeId("review-router").type("ROUTER").build(),
                        OpsWorkflowNode.builder().nodeId("rag").type("AGENT").agent("rag-knowledge-agent").ragEnabled(true).build(),
                        OpsWorkflowNode.builder().nodeId("report").type("REPORT").build()))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("rag")
                                .conditionType("review_decision")
                                .condition("needs:rag")
                                .feedback(true)
                                .build(),
                        OpsGraphEdge.builder()
                                .from("review-router")
                                .to("report")
                                .conditionType("default")
                                .condition("final_report")
                                .defaultEdge(true)
                                .build()))
                .loops(List.of(OpsLoopPolicy.builder()
                        .loopId("investigation")
                        .feedbackEdges(List.of("review-router->rag"))
                        .exitEdge("review-router->report")
                        .maxRounds(2)
                        .build()))
                .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent("REPLAN_CONTINUE")
                .tasks(List.of(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                        .source("rag")
                        .agent("rag-knowledge-agent")
                        .goal("补查知识库")
                        .priority(1)
                        .build()))
                .build();
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> existingResults = List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("rag")
                        .agent("rag-knowledge-agent")
                        .status("INSUFFICIENT")
                        .summary("缺少历史案例。")
                        .build());

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO allowed = coordinator.filterGraphReplanTasks(
                plan, existingResults, definition, new OpsAgentRunRequestDTO(),
                new OverAllState(Map.of("loopRounds", Map.of("investigation", 1))));
        assertFalse(allowed.getTasks().isEmpty());

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO exhausted = coordinator.filterGraphReplanTasks(
                plan, existingResults, definition, new OpsAgentRunRequestDTO(),
                new OverAllState(Map.of("loopRounds", Map.of("investigation", 2))));
        assertTrue(exhausted.getTasks().isEmpty());
    }

    @Test
    void shouldRenderOnlyConfiguredContextInputsInGenericNodePrompt() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("agent")
                .type("AGENT")
                .agent("custom-agent")
                .description("自定义节点")
                .config(Map.of("contextInputs", List.of("query", "state.plan", "memoryContext", "upstreamOutputs")))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(node))
                .build();
        OverAllState state = new OverAllState(Map.of(
                "query", "重写后的问题",
                "plan", "只查 prometheus",
                "memoryContext", "上一轮提到 join 接口异常",
                "hiddenState", "不应直接暴露"));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("重写后的问题")
                .build();

        String prompt = coordinator.buildAgentScopeNodeInput(
                definition, node, request, "上游节点输出", state, nodeExecutionHooks());

        assertTrue(prompt.contains("- query:\n重写后的问题"));
        assertTrue(prompt.contains("- state.plan:\n只查 prometheus"));
        assertTrue(prompt.contains("- memoryContext:\n上一轮提到 join 接口异常"));
        assertTrue(prompt.contains("- upstreamOutputs:\n上游节点输出"));
        assertFalse(prompt.contains("hiddenState"));
    }

    @Test
    void genericFinalReportShouldReceiveCollectedInvestigationResultsByDefault() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("final-report")
                .type("AGENT")
                .agent("ops-final-report")
                .description("汇总真实调查证据")
                .config(Map.of("role", "reporter"))
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(node))
                .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO elasticsearchResult =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .agent("es-log-agent")
                        .status("FOUND")
                        .summary("Elasticsearch search 已执行，最近 5 分钟 ERROR 命中 0 条。")
                        .evidence(List.of("resultId=tool-result-es-1, outputHash=sha256-es-1, totalHits=0"))
                        .build();
        OverAllState state = new OverAllState(Map.of(
                "query", "查询最近 5 分钟 ERROR 日志",
                "results", List.of(elasticsearchResult),
                "output", "复盘完成，进入最终报告"));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("查询最近 5 分钟 ERROR 日志")
                .build();

        String prompt = coordinator.buildAgentScopeNodeInput(
                definition, node, request, "复盘完成，进入最终报告", state, nodeExecutionHooks());

        assertTrue(prompt.contains("- results:"));
        assertTrue(prompt.contains("elasticsearch"));
        assertTrue(prompt.contains("totalHits=0"));
        assertTrue(prompt.contains("- upstreamOutputs:\n复盘完成，进入最终报告"));
    }

    @Test
    void shouldWrapAgentScopeNodeInputWithRouterContract() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();
        OpsWorkflowNode reactNode = OpsWorkflowNode.builder()
                .nodeId("classify")
                .type("AGENTSCOPE")
                .agent("react-classifier")
                .description("先理解问题并决定后续数据源")
                .build();
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .description("按 ReAct 节点输出选择数据源")
                .config(Map.of("inputKey", "selectedRoutes", "routeMode", "multi"))
                .build();
        OpsWorkflowNode prometheus = OpsWorkflowNode.builder()
                .nodeId("prometheus")
                .type("AGENT")
                .description("查询 Prometheus 指标")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .nodes(List.of(reactNode, router, prometheus))
                .edges(List.of(
                        OpsGraphEdge.builder()
                                .from("classify")
                                .to("router")
                                .conditionType("always")
                                .condition("always")
                                .description("分类结果交给 Router")
                                .build(),
                        OpsGraphEdge.builder()
                                .from("router")
                                .to("prometheus")
                                .conditionType("route_match")
                                .condition("prometheus")
                                .description("需要指标趋势、QPS、错误率或延迟证据")
                                .build()))
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .query("最近接口变慢了，帮我判断应该查什么")
                .build();
        OverAllState state = new OverAllState(Map.of(
                "query", request.getQuery(),
                "output", "上游规划：先判断是否需要指标证据"));

        String input = coordinator.buildAgentScopeNodeInput(
                definition, reactNode, request, "上游规划：先判断是否需要指标证据", state,
                nodeExecutionHooks());

        assertTrue(input.contains("### 原始用户问题"));
        assertTrue(input.contains("### 当前节点\nnodeId: classify"));
        assertTrue(input.contains("### 当前节点可选出边"));
        assertTrue(input.contains("### 下游 Router 输出契约"));
        assertTrue(input.contains("\"inputKey\":\"selectedRoutes\""));
        assertTrue(input.contains("tasks[].routeKey=prometheus"));
        assertTrue(input.contains("需要指标趋势、QPS、错误率或延迟证据"));
    }

    @Test
    void shouldSkipQueuedNodesAfterGraphReportCompletes() {
        OpsGraphNodeExecutionCoordinator coordinator = nodeExecutionCoordinator();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .build();
        OpsWorkflowNode queuedRouter = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .agent("ops-router")
                .build();
        OverAllState state = new OverAllState(Map.of(
                "graphCompleted", true,
                "output", "正式报告"));

        Map<String, Object> result = coordinator.execute(
                definition,
                queuedRouter,
                OpsAgentChatRequest.builder().query("检查实例").metadata(new java.util.HashMap<>()).build(),
                state,
                new java.util.ArrayList<>(),
                null,
                nodeExecutionHooks());

        assertTrue(result.isEmpty());
        assertEquals("正式报告", state.value("output", ""));
    }

    @Test
    void completedGraphRoutesStaleRouterDirectlyToEnd() {
        OpsWorkflowNode router = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .build();
        OpsGraphEdge staleEdge = OpsGraphEdge.builder()
                .from("router")
                .to("investigation")
                .conditionType("route_match")
                .condition("elasticsearch")
                .build();

        assertEquals(List.of("__end__"), route(
                OpsAgentDefinition.builder().agentId("ops").edges(List.of(staleEdge)).build(),
                new OpsAgentRunRequestDTO(),
                new OverAllState(Map.of("graphCompleted", true, "output", "elasticsearch")),
                router,
                staleEdge));
    }

    @Test
    void completedReportStillRoutesToConfiguredNotification() {
        OpsWorkflowNode report = OpsWorkflowNode.builder()
                .nodeId("final-report")
                .type("AGENT")
                .agent("ops-final-report")
                .config(Map.of("role", "reporter"))
                .build();
        OpsGraphEdge notifyEdge = OpsGraphEdge.builder()
                .from("final-report")
                .to("channel-notify")
                .conditionType("expression")
                .condition("notifyChannel == true")
                .build();

        assertEquals(List.of("notifyChannel == true"), route(
                OpsAgentDefinition.builder().agentId("ops").edges(List.of(notifyEdge)).build(),
                new OpsAgentRunRequestDTO(),
                new OverAllState(Map.of("graphCompleted", true, "notifyChannel", true)),
                report,
                notifyEdge));
    }

    @Test
    void reportAndNotificationTypesMarkGraphCompletedAfterSuccess() {
        OpsGraphTopologyAssembler topology = topologyAssembler();

        assertTrue(topology.marksGraphCompletedOnSuccess("REPORT"));
        assertTrue(topology.marksGraphCompletedOnSuccess("NOTIFY"));
        assertTrue(topology.marksGraphCompletedOnSuccess("END"));
        assertFalse(topology.marksGraphCompletedOnSuccess("ROUTER"));
    }

    @Test
    void graphEndLifecycleIsClaimedOnlyOncePerCanonicalRun() {
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-end-once")
                .metadata(new java.util.HashMap<>())
                .build();

        assertTrue(stateManager.claimEndExecution(request));
        assertFalse(stateManager.claimEndExecution(request));
    }

    private List<String> route(OpsAgentDefinition definition,
                               OpsAgentRunRequestDTO request,
                               OverAllState state,
                               OpsWorkflowNode router,
                               OpsGraphEdge feedbackEdge) {
        return route(definition, request, state, router, List.of(feedbackEdge), false);
    }

    private List<String> route(OpsAgentDefinition definition,
                               OpsAgentRunRequestDTO request,
                               OverAllState state,
                               OpsWorkflowNode router,
                               List<OpsGraphEdge> feedbackEdges,
                               boolean parallelSelection) {
        return topologyAssembler().routeConditions(
                definition, request, state, feedbackEdges, router, "end", parallelSelection, null);
    }

    @SuppressWarnings("unchecked")
    private int loopRound(OverAllState state) {
        return loopRound(state, "investigation");
    }

    @SuppressWarnings("unchecked")
    private int loopRound(OverAllState state, String loopId) {
        Object value = state.value("loopRounds").orElse(Map.of());
        assertTrue(value instanceof Map<?, ?>);
        Object round = ((Map<String, Object>) value).get(loopId);
        return round instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(round));
    }

    private OpsAnalysisNodeExecutionCoordinator analysisNodeExecutionCoordinator() {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsGraphTopologyAssembler topology = OpsGraphTopologyAssemblerTestFactory.create(
                routingPolicy, new OpsGraphConditionEvaluator(), stateManager);
        return OpsAnalysisNodeExecutionAssembly.create(
                null, null, null, null, null, null,
                routingPolicy, stateManager, topology, null);
    }

    private OpsGraphTopologyAssembler topologyAssembler() {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        return OpsGraphTopologyAssemblerTestFactory.create(
                routingPolicy,
                new OpsGraphConditionEvaluator(),
                new OpsGraphRuntimeStateManager());
    }

    private OpsAgentScopeExecutor agentScopeExecutor() {
        return new OpsAgentScopeExecutor(
                null,
                null,
                new OpsRuntimePromptAssembler(),
                Runnable::run);
    }

    private OpsGraphNodeExecutionCoordinator nodeExecutionCoordinator() {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsGraphConditionEvaluator conditionEvaluator = new OpsGraphConditionEvaluator();
        OpsGraphRuntimeStateManager stateManager = new OpsGraphRuntimeStateManager();
        OpsRuntimePromptAssembler promptAssembler = new OpsRuntimePromptAssembler();
        OpsAgentScopeExecutor agentScopeExecutor = new OpsAgentScopeExecutor(
                null, null, promptAssembler, Runnable::run);
        OpsAgentScopeExecutionCoordinator agentScopeExecutionCoordinator =
                new OpsAgentScopeExecutionCoordinator(
                        agentScopeExecutor,
                        null,
                        ignored -> {
                        },
                        () -> null);
        OpsGraphTopologyAssembler topology = OpsGraphTopologyAssemblerTestFactory.create(
                routingPolicy, conditionEvaluator, stateManager);
        OpsAnalysisNodeExecutionCoordinator analysisCoordinator =
                OpsAnalysisNodeExecutionAssembly.create(
                        null, null, null, null, null, null,
                        routingPolicy, stateManager, topology, null);
        return OpsGraphNodeExecutionAssembly.create(
                org.mockito.Mockito.mock(OpsRuntimeResourceAssembler.class),
                null,
                promptAssembler,
                null,
                agentScopeExecutionCoordinator,
                stateManager,
                topology,
                null,
                analysisCoordinator,
                routingPolicy,
                conditionEvaluator,
                null,
                null,
                null,
                null);
    }

    private OpsGraphNodeExecutionCoordinator.Hooks nodeExecutionHooks() {
        return new OpsGraphNodeExecutionCoordinator.Hooks() {
            @Override
            public void assertNotCanceled(OpsAgentChatRequest request) {
            }

            @Override
            public String executionNodeType(OpsWorkflowNode node) {
                return node == null || node.getType() == null ? "CHAT" : node.getType();
            }

            @Override
            public OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks() {
                return OpsGraphFeedbackLoopRuntimeTest.this.analysisNodeHooks();
            }

            @Override
            public long requestStartedNanos(OpsAgentChatRequest request) {
                return System.nanoTime();
            }
        };
    }

    private OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks() {
        return new OpsAnalysisNodeExecutionCoordinator.Hooks() {
            @Override
            public String executionNodeType(OpsWorkflowNode node) {
                return node == null || node.getType() == null ? "CHAT" : node.getType();
            }

            @Override
            public String evaluateChangePackage(OpsAgentDefinition definition,
                                                OpsWorkflowNode node,
                                                OpsAgentChatRequest request,
                                                OpsAnalysisResponseDTO response,
                                                List<OpsRuntimeEvent> events,
                                                java.util.function.Consumer<OpsRuntimeEvent> eventSink) {
                return "";
            }
        };
    }
}
