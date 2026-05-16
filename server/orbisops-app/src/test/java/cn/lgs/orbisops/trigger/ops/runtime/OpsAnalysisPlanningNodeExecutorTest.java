package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisPlanningNodeExecutorTest {

    @Test
    void wholePlanExecutionPublishesCompletedBatchAsInitialReviewResults() {
        OpsInvestigationExecutor investigations = mock(OpsInvestigationExecutor.class);
        OpsAnalysisResponseDTO.InvestigationResultDTO prometheus = result("prometheus");
        OpsAnalysisResponseDTO.InvestigationResultDTO elasticsearch = result("elasticsearch");
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> completed =
                List.of(prometheus, elasticsearch);
        when(investigations.execute(any(), any(), any(), any())).thenReturn(completed);

        OpsAnalysisPlanningNodeExecutor executor = new OpsAnalysisPlanningNodeExecutor(
                mock(OpsMainAgentPlanner.class),
                investigations,
                mock(OpsAnalysisPlanLoopCoordinator.class),
                mock(OpsAnalysisRoutingPolicy.class),
                mock(OpsGraphRuntimeStateManager.class),
                mock(OpsGraphTopologyAssembler.class),
                mock(OpsAnalysisNodeLifecycle.class));

        List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults = new ArrayList<>();
        AtomicReference<List<OpsAnalysisResponseDTO.InvestigationResultDTO>> resultRef =
                new AtomicReference<>(List.of());
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan =
                OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder().intent("INCIDENT").build();
        OpsAnalysisRuntimeStateManager.State state = new OpsAnalysisRuntimeStateManager.State(
                OpsAgentRunRequestDTO.builder().runId("run-1").build(),
                OpsAnalysisResponseDTO.builder().analysisId("run-1").build(),
                mock(OpsQuestionContext.class),
                new ArrayList<>(),
                new ArrayList<>(),
                initialResults,
                new AtomicInteger(),
                new LinkedHashSet<>(),
                new AtomicReference<>(plan),
                resultRef,
                new AtomicBoolean(),
                new AtomicBoolean());
        OpsAnalysisNodeExecutionContext context = new OpsAnalysisNodeExecutionContext(
                OpsAgentDefinition.builder().agentId("agent-1").build(),
                OpsWorkflowNode.builder().nodeId("execute").type("EXECUTE").build(),
                OpsAgentChatRequest.builder().runId("run-1").build(),
                state,
                mock(OverAllState.class),
                new ArrayList<>(),
                ignored -> { },
                null,
                "EXECUTE",
                "2026-08-09 12:00:00",
                1L,
                new LinkedHashMap<>());

        executor.executePlanTasks(context);

        assertEquals(completed, initialResults);
        assertSame(completed, resultRef.get());
        assertEquals(completed, context.output().get("results"));
    }

    private OpsAnalysisResponseDTO.InvestigationResultDTO result(String source) {
        return OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                .source(source)
                .status("FOUND")
                .build();
    }
}
