package cn.lgs.orbisops.domain.investigation.model;

/** Deterministic operations insight emitted from investigation evidence. */
public record InvestigationInsight(
        String level,
        String title,
        String detail,
        String suggestion) {
}
