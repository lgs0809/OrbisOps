package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutor;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Executes and merges source-scoped immediate review follow-ups. */
final class OpsAnalysisImmediateFollowUpCoordinator {

    private final OpsInvestigationExecutor investigationExecutor;
    private final OpsAnalysisRuntimeStateManager stateManager;
    private final OpsAnalysisRoutingPolicy routingPolicy;

    OpsAnalysisImmediateFollowUpCoordinator(
            OpsInvestigationExecutor investigationExecutor,
            OpsAnalysisRuntimeStateManager stateManager,
            OpsAnalysisRoutingPolicy routingPolicy) {
        this.investigationExecutor = investigationExecutor;
        this.stateManager = stateManager;
        this.routingPolicy = routingPolicy;
    }

    void trigger(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            OpsQuestionContext questionContext,
            List<OpsAnalysisResponseDTO.AgentExecutionStepDTO> steps,
            List<String> runtimeNotes,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> initialResults,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
            Set<String> immediateFollowUpSources,
            AtomicReference<List<OpsAnalysisResponseDTO.InvestigationResultDTO>> resultRef) {
        if (routingPolicy.maxMainRounds(definition, request) <= 1
                || latestResult == null
                || !StringUtils.hasText(latestResult.getSource())) {
            return;
        }
        if (!OpsAnalysisRoutingPolicy.REVIEW_MODE_IMMEDIATE.equals(
                routingPolicy.reviewMode(definition))) {
            return;
        }
        String source = routingPolicy.normalizeSource(latestResult.getSource());
        if (!immediateFollowUpSources.add(source)) return;

        List<OpsAnalysisResponseDTO.InvestigationResultDTO> snapshot;
        synchronized (initialResults) {
            snapshot = new ArrayList<>(initialResults);
        }
        int initialSize = snapshot.size();
        OpsInvestigationExecutor.FollowUpOutcome outcome =
                investigationExecutor.executeGraphImmediateFollowUps(
                        request,
                        response,
                        plan,
                        questionContext,
                        snapshot,
                        latestResult,
                        routingPolicy.plannedSources(plan));
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> followUpResults =
                new ArrayList<>(Optional.ofNullable(outcome.results()).orElse(List.of()));
        if (followUpResults.size() > initialSize) {
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> additions =
                    followUpResults.subList(initialSize, followUpResults.size());
            synchronized (initialResults) {
                initialResults.addAll(additions);
                List<OpsAnalysisResponseDTO.InvestigationResultDTO> merged =
                        new ArrayList<>(initialResults);
                response.setInvestigationResults(merged);
                resultRef.set(merged);
            }
            stateManager.recordFollowUpSteps(
                    steps, request, response, initialSize, followUpResults);
        }
        synchronized (runtimeNotes) {
            runtimeNotes.addAll(prefixNotes(source, outcome.executionNotes()));
        }
    }

    private List<String> prefixNotes(String source, List<String> notes) {
        return Optional.ofNullable(notes).orElse(List.of()).stream()
                .map(note -> "主 Agent 即时复盘[" + source + "]：" + note)
                .toList();
    }
}
