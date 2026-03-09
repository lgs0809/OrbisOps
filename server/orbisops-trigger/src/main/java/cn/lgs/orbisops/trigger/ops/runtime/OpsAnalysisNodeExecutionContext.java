package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Canonical state passed between Analysis node protocol handlers. */
record OpsAnalysisNodeExecutionContext(
        OpsAgentDefinition definition,
        OpsWorkflowNode node,
        OpsAgentChatRequest runtimeRequest,
        OpsAnalysisRuntimeStateManager.State analysisState,
        OverAllState graphState,
        List<OpsRuntimeEvent> events,
        Consumer<OpsRuntimeEvent> eventSink,
        OpsAnalysisNodeExecutionCoordinator.Hooks hooks,
        String nodeType,
        String startedAt,
        long startedMillis,
        Map<String, Object> output) {

    OpsAgentRunRequestDTO request() {
        return analysisState.analysisRequest();
    }

    OpsAnalysisResponseDTO response() {
        return analysisState.response();
    }

    OpsQuestionContext questionContext() {
        return analysisState.questionContext();
    }

    List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps() {
        return analysisState.steps();
    }

    List<String> runtimeNotes() {
        return analysisState.runtimeNotes();
    }

    List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults() {
        return analysisState.initialResults();
    }

    Set<String> immediateFollowUpSources() {
        return analysisState.immediateFollowUpSources();
    }

    AtomicReference<OpsAnalysisResponseDTO.OpsInvestigationPlanDTO> planRef() {
        return analysisState.planRef();
    }

    AtomicReference<List<OpsAnalysisResponseDTO.InvestigationResultDTO>> resultRef() {
        return analysisState.resultRef();
    }
}
