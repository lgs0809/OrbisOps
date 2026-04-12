package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;

/** Framework-free plan used by deterministic Investigation planning and guardrails. */
public record InvestigationPlanningPlan(
        String intent,
        String reason,
        Boolean changeRequested,
        String changeIntent,
        List<InvestigationPlanningTask> tasks,
        List<InvestigationPlanningTask> conditionalTasks,
        List<InvestigationPlanningTask> skippedTasks) {
}
