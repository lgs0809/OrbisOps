package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsGraphRuntimeStateManagerTest {

    private final OpsGraphRuntimeStateManager manager = new OpsGraphRuntimeStateManager();
    private final OpsAnalysisRoutingPolicy routingPolicy = new OpsAnalysisRoutingPolicy();

    @Test
    void initializesTracksAndCleansTransientRunState() {
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-graph-1")
                .query("check logs")
                .metadata(new LinkedHashMap<>())
                .build();

        manager.initialize(request);

        assertFalse(manager.investigationExecuted(request));
        assertEquals(Boolean.FALSE,
                request.getMetadata().get(OpsGraphRuntimeStateManager.RUNTIME_INVESTIGATION_EXECUTED_KEY));
        assertTrue(manager.claimEndExecution(request));
        assertFalse(manager.claimEndExecution(request));

        manager.markInvestigationExecuted(request, true);
        assertTrue(manager.investigationExecuted(request));

        manager.cleanup(request);
        assertTrue(manager.claimEndExecution(request));
    }

    @Test
    void projectsInitialGraphInputWithoutOwningExecutionPolicy() {
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-graph-2")
                .query("check metrics")
                .metadata(new LinkedHashMap<>())
                .build();

        Map<String, Object> input = manager.initialInput(
                request,
                null,
                "original question",
                "remembered context",
                Set.of("prometheus"),
                Set.of("elasticsearch"));

        assertEquals("check metrics", input.get("query"));
        assertEquals("original question", input.get("originalQuery"));
        assertEquals("remembered context", input.get("memoryContext"));
        assertEquals(Boolean.FALSE, input.get(OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY));
        assertEquals(List.of("prometheus"), input.get("intentAllowedRoutes"));
        assertEquals(List.of("elasticsearch"), input.get("intentExcludedRoutes"));
        assertTrue(manager.keyStrategyFactory() != null);
    }

    @Test
    void detectsTrackedSingleSourceInvestigationAcrossGraphState() {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("allowedInvestigationSources", List.of("logs"));
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-graph-3")
                .metadata(metadata)
                .build();
        manager.initialize(request);

        OpsAnalysisResponseDTO.InvestigationResultDTO observation =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("logs")
                        .summary("error sample found")
                        .build();
        Map<String, Object> nodeResult = new LinkedHashMap<>();
        nodeResult.put("latestObservation", observation);
        manager.markAgentScopeInvestigationCompleted(request, nodeResult, routingPolicy);

        OverAllState graphState = new OverAllState(Map.of(
                OpsGraphRuntimeStateManager.INVESTIGATION_EXECUTED_KEY, true,
                "results", List.of(observation)));

        assertTrue(manager.completedExplicitSingleSourceGraphInvestigation(
                request, graphState, routingPolicy));
        assertEquals(Set.of("elasticsearch"),
                manager.graphStateExecutedSources(graphState, routingPolicy));
    }

    @Test
    void runtimeCannotReclaimTransientGraphStateOwnership() {
        Set<String> forbiddenFields = Set.of(
                "graphInvestigationExecutionState",
                "graphExecutedSourceState",
                "graphEndExecutionState");
        Set<String> forbiddenMethods = Set.of(
                "keyStrategyFactory",
                "claimGraphEndExecution",
                "graphStateExecutedSources",
                "analysisResultSnapshot",
                "markRuntimeInvestigationExecuted",
                "markAgentScopeInvestigationCompleted",
                "runtimeInvestigationExecuted",
                "completedExplicitSingleSourceInvestigation",
                "completedExplicitSingleSourceGraphInvestigation");
        Set<String> declaredFields = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        Set<String> declaredMethods = Arrays.stream(OpsWorkSessionLifecycleCoordinator.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(forbiddenFields.stream().noneMatch(declaredFields::contains));
        assertTrue(forbiddenMethods.stream().noneMatch(declaredMethods::contains));
    }
}
