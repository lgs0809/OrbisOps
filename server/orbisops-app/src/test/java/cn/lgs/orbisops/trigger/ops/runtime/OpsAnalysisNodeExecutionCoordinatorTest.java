package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAnalysisNodeExecutionCoordinatorTest {

    @Test
    void structuralNodeUsesCanonicalAnalysisStateAndRecordsStep() {
        OpsAnalysisRuntimeStateManager stateManager = stateManager();
        OpsAnalysisNodeExecutionCoordinator coordinator = coordinator(stateManager);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops")
                .version(1)
                .engine("GRAPH")
                .build();
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put(OpsAnalysisRuntimeMetadata.REQUEST_KEY, new OpsAgentRunRequestDTO());
        metadata.put(OpsAnalysisRuntimeMetadata.RESPONSE_KEY, response);
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("analysis-structural")
                .query("检查实例")
                .metadata(metadata)
                .build();
        OpsAnalysisRuntimeStateManager.State state = stateManager.ensure(definition, request);
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("start")
                .type("START")
                .build();

        Map<String, Object> output = coordinator.execute(
                definition,
                node,
                request,
                state,
                new OverAllState(Map.of()),
                new java.util.ArrayList<>(),
                null,
                hooks());

        assertEquals("结构节点透传：start", output.get("output"));
        assertEquals(response, output.get("response"));
        assertEquals(1, state.steps().size());
        assertEquals("START", state.steps().get(0).getNodeType());
    }

    @Test
    void replanFilterRespectsAuthoritativeIntentAndExecutedSources() {
        OpsAnalysisNodeExecutionCoordinator coordinator = coordinator(null);
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("allowedInvestigationSources", List.of("ELASTICSEARCH"));
        metadata.put("excludedCapabilities", List.of("PROMETHEUS", "RAG"));
        OpsAgentChatRequest runtimeRequest = OpsAgentChatRequest.builder()
                .metadata(metadata)
                .build();
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO proposed =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                        .tasks(List.of(
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source("elasticsearch").priority(1).build(),
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source("prometheus").priority(2).build(),
                                OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                                        .source("rag").priority(3).build()))
                        .build();

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filtered = coordinator.filterGraphReplanTasks(
                proposed,
                List.of(),
                OpsAgentDefinition.builder().build(),
                new OpsAgentRunRequestDTO(),
                runtimeRequest,
                new OverAllState(Map.of()));
        assertEquals(List.of("elasticsearch"), filtered.getTasks().stream()
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .toList());

        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO afterExecution = coordinator.filterGraphReplanTasks(
                proposed,
                List.of(OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("elasticsearch")
                        .status("SUCCEEDED")
                        .build()),
                OpsAgentDefinition.builder().build(),
                new OpsAgentRunRequestDTO(),
                runtimeRequest,
                new OverAllState(Map.of()));
        assertTrue(afterExecution.getTasks().isEmpty());
    }

    @Test
    void runtimeCannotReclaimAnalysisNodeProtocol() {
        Set<String> forbiddenMethods = Set.of(
                "executeAnalysisNode",
                "filterGraphReplanTasks",
                "ensureAnalysisPlan",
                "triggerImmediateAnalysisFollowUps",
                "runAnalysisMainPlanExecuteLoop",
                "recordAnalysisStep",
                "waitForInitialAnalysisSubAgents",
                "selectedRoutesForRouter",
                "graphNodeAnalysisTask");
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
        assertFalse(Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredClasses())
                .anyMatch(type -> "MainLoopOutcome".equals(type.getSimpleName())));
    }

    private OpsAnalysisNodeExecutionCoordinator coordinator(
            OpsAnalysisRuntimeStateManager stateManager) {
        OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();
        OpsGraphRuntimeStateManager graphStateManager = new OpsGraphRuntimeStateManager();
        OpsGraphTopologyAssembler topology = OpsGraphTopologyAssemblerTestFactory.create(
                routingPolicy,
                new OpsGraphConditionEvaluator(),
                graphStateManager);
        return OpsAnalysisNodeExecutionAssembly.create(
                null,
                null,
                null,
                null,
                stateManager,
                null,
                routingPolicy,
                graphStateManager,
                topology,
                null);
    }

    private OpsAnalysisRuntimeStateManager stateManager() {
        return OpsAnalysisRuntimeStateManagerTestFactory.create();
    }

    private OpsAnalysisNodeExecutionCoordinator.Hooks hooks() {
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
