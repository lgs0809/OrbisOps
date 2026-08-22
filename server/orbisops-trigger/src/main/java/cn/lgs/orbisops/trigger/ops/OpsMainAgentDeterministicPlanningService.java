package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningSignals;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningTask;
import cn.lgs.orbisops.domain.investigation.service.InvestigationPlanningPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** DTO anti-corruption layer for deterministic Investigation planning. */
final class OpsMainAgentDeterministicPlanningService {

    private static final InvestigationPlanningPolicy POLICY = new InvestigationPlanningPolicy();

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO initial(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            Set<String> availableSources) {
        return toDto(POLICY.initialPlan(new InvestigationPlanningPolicy.InitialInput(
                signals(context),
                request == null ? null : request.getIncludeRecentLogs(),
                availableSources)));
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO replan(
            OpsQuestionContext context,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            int round,
            int maxRounds,
            Set<String> availableSources) {
        return toDto(POLICY.replan(new InvestigationPlanningPolicy.ReplanInput(
                signals(context),
                observations(results),
                round,
                maxRounds,
                availableSources)));
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO guardrails(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO llmPlan,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallbackPlan,
            OpsQuestionContext context) {
        return toDto(POLICY.applyGuardrails(new InvestigationPlanningPolicy.GuardrailInput(
                toDomain(llmPlan),
                toDomain(fallbackPlan),
                signals(context))));
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO allSources(
            OpsQuestionContext context,
            Set<String> availableSources) {
        return toDto(POLICY.allSourcesPlan(new InvestigationPlanningPolicy.AllSourcesInput(
                signals(context),
                availableSources)));
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO allSourcesReplanStop(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previousPlan) {
        return toDto(POLICY.allSourcesReplanStop(toDomain(previousPlan)));
    }

    String normalizeSource(String source) {
        return InvestigationPlanningPolicy.normalizeSource(source);
    }

    private InvestigationPlanningSignals signals(OpsQuestionContext context) {
        if (context == null) {
            return new InvestigationPlanningSignals("", "", "", false, false, false, false, false, false, false);
        }
        return new InvestigationPlanningSignals(
                context.originalQuestion(),
                context.loweredQuestion(),
                context.describeFilters(),
                context.blankQuestion(),
                context.hasLogSignal(),
                context.hasMetricSignal(),
                context.hasSlowSqlSignal(),
                context.hasKnowledgeSignal(),
                !context.traceIds().isEmpty(),
                !context.entityIds().isEmpty());
    }

    List<InvestigationPlanningObservation> observations(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        return Optional.ofNullable(results).orElse(List.of()).stream()
                .map(result -> result == null ? null : new InvestigationPlanningObservation(
                        result.getSource(),
                        result.getStatus(),
                        result.getSummary(),
                        result.getEvidence(),
                        result.getGaps(),
                        result.getSuggestedAdjustments()))
                .toList();
    }

    InvestigationPlanningPlan toDomain(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        if (plan == null) {
            return null;
        }
        return new InvestigationPlanningPlan(
                plan.getIntent(),
                plan.getReason(),
                plan.getChangeRequested(),
                plan.getChangeIntent(),
                tasks(plan.getTasks()),
                tasks(plan.getConditionalTasks()),
                tasks(plan.getSkippedTasks()));
    }

    private List<InvestigationPlanningTask> tasks(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .map(task -> task == null ? null : new InvestigationPlanningTask(
                        task.getSource(),
                        task.getAgent(),
                        task.getGoal(),
                        task.getReason(),
                        task.getPriority(),
                        task.getCondition()))
                .toList();
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO toDto(InvestigationPlanningPlan plan) {
        if (plan == null) {
            return null;
        }
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent(plan.intent())
                .reason(plan.reason())
                .changeRequested(plan.changeRequested())
                .changeIntent(plan.changeIntent())
                .tasks(dtoTasks(plan.tasks()))
                .conditionalTasks(dtoTasks(plan.conditionalTasks()))
                .skippedTasks(dtoTasks(plan.skippedTasks()))
                .build();
    }

    private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> dtoTasks(
            List<InvestigationPlanningTask> tasks) {
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> result = new ArrayList<>();
        for (InvestigationPlanningTask task : Optional.ofNullable(tasks).orElse(List.of())) {
            if (task == null) {
                result.add(null);
                continue;
            }
            result.add(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                    .source(task.source())
                    .agent(task.agent())
                    .goal(task.goal())
                    .reason(task.reason())
                    .priority(task.priority())
                    .condition(task.condition())
                    .build());
        }
        return result;
    }
}
