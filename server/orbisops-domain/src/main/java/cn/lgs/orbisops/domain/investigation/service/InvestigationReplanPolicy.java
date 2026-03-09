package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningObservation;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningPlan;
import cn.lgs.orbisops.domain.investigation.model.InvestigationPlanningTask;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Preserves explicit change intent and removes already executed sources from replan candidates. */
public final class InvestigationReplanPolicy {

    public InvestigationPlanningPlan inheritChangeIntent(
            InvestigationPlanningPlan target,
            InvestigationPlanningPlan source) {
        if (target == null || source == null) return target;
        Boolean changeRequested = source.changeRequested() != null
                ? source.changeRequested()
                : target.changeRequested();
        String changeIntent = hasText(source.changeIntent())
                ? source.changeIntent()
                : target.changeIntent();
        return copy(target, changeRequested, changeIntent,
                target.tasks(), target.conditionalTasks(), target.skippedTasks());
    }

    public InvestigationPlanningPlan excludeExecutedSources(
            InvestigationPlanningPlan plan,
            List<InvestigationPlanningObservation> observations) {
        if (plan == null) return null;
        Set<String> executed = Optional.ofNullable(observations).orElse(List.of()).stream()
                .filter(observation -> observation != null && hasText(observation.source()))
                .map(InvestigationPlanningObservation::source)
                .map(InvestigationPlanningPolicy::normalizeSource)
                .collect(Collectors.toSet());
        return copy(
                plan,
                plan.changeRequested(),
                plan.changeIntent(),
                remaining(plan.tasks(), executed),
                remaining(plan.conditionalTasks(), executed),
                plan.skippedTasks());
    }

    private List<InvestigationPlanningTask> remaining(
            List<InvestigationPlanningTask> tasks,
            Set<String> executed) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .filter(java.util.Objects::nonNull)
                .filter(task -> !executed.contains(
                        InvestigationPlanningPolicy.normalizeSource(task.source())))
                .toList();
    }

    private InvestigationPlanningPlan copy(
            InvestigationPlanningPlan source,
            Boolean changeRequested,
            String changeIntent,
            List<InvestigationPlanningTask> tasks,
            List<InvestigationPlanningTask> conditionalTasks,
            List<InvestigationPlanningTask> skippedTasks) {
        return new InvestigationPlanningPlan(
                source.intent(),
                source.reason(),
                changeRequested,
                changeIntent,
                tasks,
                conditionalTasks,
                skippedTasks);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
