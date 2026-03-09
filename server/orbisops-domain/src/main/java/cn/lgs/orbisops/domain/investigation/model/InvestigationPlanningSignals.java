package cn.lgs.orbisops.domain.investigation.model;

/** Stable question signals consumed by deterministic Investigation planning policy. */
public record InvestigationPlanningSignals(
        String originalQuestion,
        String loweredQuestion,
        String filtersDescription,
        boolean blankQuestion,
        boolean logSignal,
        boolean metricSignal,
        boolean slowSqlSignal,
        boolean knowledgeSignal,
        boolean traceIdPresent,
        boolean orderIdPresent) {
}
