package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphNodeExecutionCoordinatorTest {

    @Test
    void startNodePublishesLifecycleAndProjectsOutput() {
        OpsGraphNodeExecutionCoordinator coordinator = coordinator();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("start")
                .type("START")
                .build();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .engine("GRAPH")
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-start")
                .query("检查实例")
                .metadata(new HashMap<>())
                .build();
        List<OpsRuntimeEvent> events = new ArrayList<>();

        Map<String, Object> result = coordinator.execute(
                definition,
                node,
                request,
                new OverAllState(Map.of("output", "上游输入")),
                events,
                null,
                hooks());

        assertEquals("上游输入", result.get("output"));
        assertEquals("上游输入", result.get("start"));
        assertEquals(List.of("NODE_START", "NODE_END"), events.stream()
                .map(OpsRuntimeEvent::getEventType)
                .toList());
    }

    @Test
    void endNodeUsesChangePackageDecisionAsAuthoritativeFinalOutput() {
        OpsGraphNodeExecutionCoordinator coordinator = coordinator();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("end")
                .type("END")
                .build();
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-end-package")
                .query("prepare change")
                .metadata(new HashMap<>())
                .build();
        String decision = "CHANGE_PACKAGE_CREATED: cp-1";

        Map<String, Object> result = coordinator.execute(
                OpsAgentDefinition.builder().agentId("ops").changePackageEnabled(true).build(),
                node,
                request,
                new OverAllState(Map.of("output", "Exception: I/O error on POST request")),
                new ArrayList<>(),
                null,
                hooks(decision));

        assertEquals(decision, result.get("output"));
        assertEquals(decision, result.get("changePackageDecision"));
        assertFalse(OpsAgentOutputGuard.isErrorEnvelope(String.valueOf(result.get("output"))));
    }

    @Test
    void completedGraphSkipsQueuedNonTerminalNode() {
        OpsGraphNodeExecutionCoordinator coordinator = coordinator();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("router")
                .type("ROUTER")
                .build();

        Map<String, Object> result = coordinator.execute(
                OpsAgentDefinition.builder().agentId("ops").build(),
                node,
                OpsAgentChatRequest.builder()
                        .runId("run-completed")
                        .query("检查实例")
                        .metadata(new HashMap<>())
                        .build(),
                new OverAllState(Map.of(
                        OpsGraphRuntimeStateManager.GRAPH_COMPLETED_KEY, true,
                        "output", "正式报告")),
                new ArrayList<>(),
                null,
                hooks());

        assertTrue(result.isEmpty());
    }

    @Test
    void agentScopeObservationUsesGraphRouteSource() {
        OpsGraphNodeExecutionCoordinator coordinator = coordinator();
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .edges(List.of(OpsGraphEdge.builder()
                        .from("router")
                        .to("es-investigation")
                        .condition("elasticsearch")
                        .build()))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("es-investigation")
                .type("AGENTSCOPE")
                .agent("es-log-agent")
                .build();

        Map<String, Object> result = coordinator.createAgentScopeObservationResult(
                definition, node, "查询完成，命中 0 条。");
        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                (OpsAnalysisResponseDTO.InvestigationResultDTO) result.get("latestObservation");

        assertEquals("elasticsearch", observation.getSource());
        assertEquals("FOUND", observation.getStatus());
        assertEquals(1, ((List<?>) result.get("results")).size());
    }

    @Test
    void runtimeCannotReclaimGraphNodeExecutionResponsibilities() {
        Set<String> forbiddenMethods = Set.of(
                "executeGraphNode",
                "executeSingleAgentScopeNode",
                "buildAgentScopeNodeInput",
                "recordAgentScopeAnalysisObservation",
                "createAgentScopeObservationResult",
                "promptContextPolicy",
                "conditionContext",
                "graphReviewDecisionIsFinalReport");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredClasses())
                .anyMatch(type -> "NodeExecutionResult".equals(type.getSimpleName())));
    }

    private OpsGraphNodeExecutionCoordinator coordinator() {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsGraphConditionEvaluator conditionEvaluator = new OpsGraphConditionEvaluator();
        OpsGraphRuntimeStateManager graphStateManager = new OpsGraphRuntimeStateManager();
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
                routingPolicy, conditionEvaluator, graphStateManager);
        OpsAnalysisRuntimeStateManager analysisStateManager =
                OpsAnalysisRuntimeStateManagerTestFactory.create();
        OpsAnalysisNodeExecutionCoordinator analysisCoordinator =
                OpsAnalysisNodeExecutionAssembly.create(
                        null, null, null, null, analysisStateManager, null,
                        routingPolicy, graphStateManager, topology, null);
        return OpsGraphNodeExecutionAssembly.create(
                org.mockito.Mockito.mock(OpsRuntimeResourceAssembler.class),
                null,
                promptAssembler,
                null,
                agentScopeExecutionCoordinator,
                graphStateManager,
                topology,
                analysisStateManager,
                analysisCoordinator,
                routingPolicy,
                conditionEvaluator,
                null,
                null,
                null,
                null);
    }

    private OpsGraphNodeExecutionCoordinator.Hooks hooks() {
        return hooks("");
    }

    private OpsGraphNodeExecutionCoordinator.Hooks hooks(String changePackageDecision) {
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
                return OpsGraphNodeExecutionCoordinatorTest.this.analysisNodeHooks(changePackageDecision);
            }

            @Override
            public long requestStartedNanos(OpsAgentChatRequest request) {
                return System.nanoTime();
            }
        };
    }

    private OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks() {
        return analysisNodeHooks("");
    }

    private OpsAnalysisNodeExecutionCoordinator.Hooks analysisNodeHooks(String changePackageDecision) {
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
                return changePackageDecision;
            }
        };
    }
}
