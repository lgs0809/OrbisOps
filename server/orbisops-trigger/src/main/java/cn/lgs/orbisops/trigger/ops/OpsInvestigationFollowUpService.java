package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpDecision;
import cn.lgs.orbisops.domain.investigation.model.InvestigationFollowUpTask;
import cn.lgs.orbisops.domain.investigation.model.InvestigationObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationQuestionSignals;
import cn.lgs.orbisops.domain.investigation.service.InvestigationFollowUpPolicy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** DTO anti-corruption layer for deterministic Investigation follow-up policy. */
final class OpsInvestigationFollowUpService {

    private static final InvestigationFollowUpPolicy POLICY =
            new InvestigationFollowUpPolicy();

    Decision decide(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                    List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
                    OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
                    Set<String> executedSources,
                    Set<String> queuedSources,
                    OpsQuestionContext questionContext,
                    boolean mysqlSlowSqlEnabled) {
        InvestigationFollowUpDecision decision = POLICY.decide(
                new InvestigationFollowUpPolicy.Input(
                        plan == null ? "" : text(plan.getIntent()),
                        tasks(plan == null ? List.of() : plan.getConditionalTasks()),
                        observations(results),
                        observation(latestResult),
                        frozen(executedSources),
                        frozen(queuedSources),
                        questionSignals(questionContext),
                        mysqlSlowSqlEnabled));
        return new Decision(
                decision.tasks().stream().map(this::task).toList(),
                decision.notes());
    }

    boolean shouldStopAfterKnowledgeObservation(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
            OpsQuestionContext questionContext) {
        return POLICY.shouldStopAfterKnowledgeObservation(
                plan == null ? "" : text(plan.getIntent()),
                observation(latestResult),
                questionSignals(questionContext));
    }

    private List<InvestigationFollowUpTask> tasks(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<InvestigationFollowUpTask> result = new ArrayList<>();
        for (OpsAnalysisResponseDTO.InvestigationTaskDTO task : source) {
            if (task == null) continue;
            result.add(new InvestigationFollowUpTask(
                    text(task.getSource()),
                    text(task.getAgent()),
                    text(task.getGoal()),
                    text(task.getReason()),
                    task.getPriority(),
                    text(task.getCondition())));
        }
        return List.copyOf(result);
    }

    private List<InvestigationObservation> observations(
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<InvestigationObservation> result = new ArrayList<>();
        for (OpsAnalysisResponseDTO.InvestigationResultDTO observation : source) {
            InvestigationObservation mapped = observation(observation);
            if (mapped != null) result.add(mapped);
        }
        return List.copyOf(result);
    }

    private InvestigationObservation observation(
            OpsAnalysisResponseDTO.InvestigationResultDTO source) {
        if (source == null) return null;
        return new InvestigationObservation(
                text(source.getSource()),
                text(source.getStatus()),
                text(source.getSummary()),
                strings(source.getEvidence()),
                strings(source.getGaps()));
    }

    private InvestigationQuestionSignals questionSignals(
            OpsQuestionContext context) {
        OpsQuestionContext safe = context == null ? OpsQuestionContext.from("") : context;
        boolean explicitRuntimeFilter = !safe.traceIds().isEmpty()
                || !safe.entityIds().isEmpty()
                || !safe.uris().isEmpty()
                || !safe.logLevels().isEmpty();
        return new InvestigationQuestionSignals(
                safe.loweredQuestion(),
                safe.hasLogSignal(),
                safe.hasMetricSignal(),
                safe.hasSlowSqlSignal(),
                safe.hasKnowledgeSignal(),
                explicitRuntimeFilter);
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(
            InvestigationFollowUpTask task) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(task.source())
                .agent(task.agent())
                .goal(task.goal())
                .reason(task.reason())
                .priority(task.priority())
                .condition(task.condition())
                .build();
    }

    private List<String> strings(List<String> values) {
        return values == null
                ? List.of()
                : java.util.Collections.unmodifiableList(new ArrayList<>(values));
    }

    private Set<String> frozen(Set<String> values) {
        return values == null
                ? Set.of()
                : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Decision(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks,
                    List<String> notes) {
        Decision {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }
}
