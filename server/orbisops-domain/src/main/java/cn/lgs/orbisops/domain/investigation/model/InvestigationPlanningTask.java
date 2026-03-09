package cn.lgs.orbisops.domain.investigation.model;

/** One deterministic Investigation planning task. */
public record InvestigationPlanningTask(
        String source,
        String agent,
        String goal,
        String reason,
        Integer priority,
        String condition) {
}
