package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Coordinates LLM reflection and deterministic follow-up routing for one observation. */
final class OpsInvestigationObservationRoutingService {

    private final OpsInvestigationReflectionService reflectionService;
    private final OpsInvestigationFollowUpService followUpService;

    OpsInvestigationObservationRoutingService(
            OpsInvestigationReflectionService reflectionService,
            OpsInvestigationFollowUpService followUpService) {
        this.reflectionService = reflectionService;
        this.followUpService = followUpService;
    }

    Decision decide(Input input) {
        List<String> notes = new ArrayList<>();
        if (input.mainReflectionLlmEnabled() && input.latestResult() != null) {
            OpsInvestigationReflectionService.Decision reflection = reflectionService.reflect(
                    new OpsInvestigationReflectionService.Input(
                            input.request(),
                            input.plan(),
                            input.latestResult(),
                            input.results(),
                            input.registeredSources(),
                            input.executedSources(),
                            input.queuedSources(),
                            input.questionContext()));
            if (reflection.valid()) {
                return acceptedReflection(input, reflection, notes);
            }
            if (hasText(reflection.fallbackNote())) {
                notes.add(reflection.fallbackNote());
            }
        }
        return deterministicDecision(input, notes);
    }

    private Decision acceptedReflection(
            Input input,
            OpsInvestigationReflectionService.Decision reflection,
            List<String> notes) {
        if (hasText(reflection.note())) {
            notes.add("LLM主 Agent 复盘：" + reflection.note());
        }
        if (followUpService.shouldStopAfterKnowledgeObservation(
                input.plan(),
                input.latestResult(),
                input.questionContext())) {
            notes.add("知识型问题已完成 RAG observation，护栏阻止追加实时数据源。");
            return new Decision(List.of(), notes, true);
        }
        if (reflection.stop()) {
            notes.add("LLM主 Agent 判断当前证据已足够，停止继续派发。");
            return new Decision(List.of(), notes, true);
        }
        reflection.tasks().forEach(task ->
                notes.add("LLM主 Agent 追加 " + task.getAgent() + "：" + task.getReason()));
        return new Decision(reflection.tasks(), notes, false);
    }

    private Decision deterministicDecision(Input input, List<String> notes) {
        OpsInvestigationFollowUpService.Decision decision = followUpService.decide(
                input.plan(),
                input.results(),
                input.latestResult(),
                input.executedSources(),
                input.queuedSources(),
                input.questionContext(),
                input.mysqlSlowSqlEnabled());
        notes.addAll(decision.notes());
        return new Decision(decision.tasks(), notes, false);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    record Input(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            Set<String> registeredSources,
            Set<String> executedSources,
            Set<String> queuedSources,
            OpsQuestionContext questionContext,
            boolean mainReflectionLlmEnabled,
            boolean mysqlSlowSqlEnabled) {
    }

    record Decision(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks,
            List<String> notes,
            boolean clearQueue) {
    }
}
