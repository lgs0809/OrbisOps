package cn.lgs.orbisops.domain.investigation.model;

import java.util.List;

/** Datasource observation projected into the deterministic replanning policy. */
public record InvestigationPlanningObservation(
        String source,
        String status,
        String summary,
        List<String> evidence,
        List<String> gaps,
        List<String> suggestedAdjustments) {
}
