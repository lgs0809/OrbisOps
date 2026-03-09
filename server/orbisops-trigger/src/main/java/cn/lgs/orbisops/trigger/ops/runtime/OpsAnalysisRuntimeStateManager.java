package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the canonical mutable state registry of analysis Graph runs. */
final class OpsAnalysisRuntimeStateManager {

    private static final String STATE_METADATA_KEY = "_opsAnalysisState";

    private final OpsAnalysisRuntimeStateFactory stateFactory;
    private final OpsAnalysisRuntimeEventRecorder eventRecorder;
    private final OpsAnalysisResponseNotes responseNotes;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    OpsAnalysisRuntimeStateManager(
            OpsAnalysisRuntimeStateFactory stateFactory,
            OpsAnalysisRuntimeEventRecorder eventRecorder,
            OpsAnalysisResponseNotes responseNotes) {
        if (stateFactory == null || eventRecorder == null || responseNotes == null) {
            throw new IllegalArgumentException("ANALYSIS_RUNTIME_STATE_DEPENDENCIES_REQUIRED");
        }
        this.stateFactory = stateFactory;
        this.eventRecorder = eventRecorder;
        this.responseNotes = responseNotes;
    }

    State ensure(OpsAgentDefinition definition, OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) {
            return null;
        }
        String runId = value(request.getRunId());
        if (StringUtils.hasText(runId)) {
            State current = states.get(runId);
            if (current != null) {
                return current;
            }
        }
        Object existing = request.getMetadata().get(STATE_METADATA_KEY);
        if (existing instanceof State state) {
            if (StringUtils.hasText(runId)) {
                states.putIfAbsent(runId, state);
            }
            return state;
        }
        Object requestValue = request.getMetadata().get(
                OpsAnalysisRuntimeMetadata.REQUEST_KEY);
        Object responseValue = request.getMetadata().get(
                OpsAnalysisRuntimeMetadata.RESPONSE_KEY);
        if (!(requestValue instanceof OpsAgentRunRequestDTO analysisRequest)
                || !(responseValue instanceof OpsAnalysisResponseDTO response)) {
            return null;
        }
        State created = stateFactory.create(
                definition, request, analysisRequest, response);
        State authoritative = StringUtils.hasText(runId)
                ? states.computeIfAbsent(runId, ignored -> created)
                : created;
        request.getMetadata().put(STATE_METADATA_KEY, authoritative);
        return authoritative;
    }

    boolean isAnalysisRequest(OpsAgentChatRequest request) {
        return request != null
                && request.getMetadata() != null
                && request.getMetadata().containsKey(
                OpsAnalysisRuntimeMetadata.REQUEST_KEY);
    }

    boolean supportsNode(String type) {
        return Set.of(
                "START", "END", "PLAN", "AGENT", "ROUTER", "EXECUTE",
                "SUB_AGENT", "RAG", "ELASTICSEARCH", "ES", "PROMETHEUS",
                "MYSQL_SLOW_SQL", "REVIEW", "REFLECT", "REPORT", "NOTIFY")
                .contains(type);
    }

    void publishRunStarted(OpsAgentDefinition definition, State state) {
        if (state == null || !state.runStarted().compareAndSet(false, true)) {
            return;
        }
        eventRecorder.publishRunStarted(
                definition, state.analysisRequest(), state.response());
    }

    boolean publishRunFinished(
            OpsAgentDefinition definition,
            State state,
            String status,
            String summary) {
        if (state == null || !state.runFinished().compareAndSet(false, true)) {
            return false;
        }
        if ("SUCCEEDED".equals(status)) {
            eventRecorder.assertNotCanceled(state.analysisRequest());
            responseNotes.mergeRuntimeNotes(state.response(), state.runtimeNotes());
            state.response().setAgentExecutionSteps(new ArrayList<>(state.steps()));
        }
        eventRecorder.publishRunFinished(
                definition,
                state.analysisRequest(),
                state.response(),
                status,
                summary);
        return true;
    }

    void remove(String runId) {
        if (StringUtils.hasText(runId)) {
            states.remove(runId);
        }
    }

    void appendExecutionNotes(
            OpsAnalysisResponseDTO response,
            List<String> notes) {
        responseNotes.append(response, notes);
    }

    void mergeRuntimeNotes(
            OpsAnalysisResponseDTO response,
            List<String> runtimeNotes) {
        responseNotes.mergeRuntimeNotes(response, runtimeNotes);
    }

    void recordStep(
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsWorkflowNode node,
            String nodeType,
            String status,
            String summary,
            String startedAt,
            long startedMillis) {
        eventRecorder.recordStep(
                steps, request, response, node, nodeType,
                status, summary, startedAt, startedMillis);
    }

    void recordFollowUpSteps(
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            int initialSize,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        eventRecorder.recordFollowUpSteps(
                steps, request, response, initialSize, results);
    }

    void assertNotCanceled(OpsAgentRunRequestDTO request) {
        eventRecorder.assertNotCanceled(request);
    }

    String now() {
        return eventRecorder.now();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record State(
            OpsAgentRunRequestDTO analysisRequest,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            List<String> runtimeNotes,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            AtomicInteger reflectionInvocations,
            Set<String> immediateFollowUpSources,
            AtomicReference<OpsAnalysisResponseDTO.OpsInvestigationPlanDTO> planRef,
            AtomicReference<List<OpsAnalysisResponseDTO.InvestigationResultDTO>> resultRef,
            AtomicBoolean runStarted,
            AtomicBoolean runFinished) {
    }
}
