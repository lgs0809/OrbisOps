package cn.lgs.orbisops.domain.investigation.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Trust decision for an LLM-generated final investigation report. */
public record InvestigationFinalReportTrustDecision(
        boolean trusted,
        Failure failure,
        List<String> violations,
        Set<String> executedSources) {

    public InvestigationFinalReportTrustDecision {
        violations = violations == null ? List.of() : List.copyOf(violations);
        executedSources = executedSources == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(executedSources));
    }

    public static InvestigationFinalReportTrustDecision trusted(
            Set<String> executedSources) {
        return new InvestigationFinalReportTrustDecision(
                true,
                Failure.NONE,
                List.of(),
                executedSources);
    }

    public static InvestigationFinalReportTrustDecision rejected(
            Failure failure,
            List<String> violations,
            Set<String> executedSources) {
        return new InvestigationFinalReportTrustDecision(
                false,
                failure,
                violations,
                executedSources);
    }

    public enum Failure {
        NONE,
        MISSING_SOURCE_OR_GAP_SECTION,
        UNEXECUTED_SOURCE_CLAIM,
        MISSING_INVESTIGATION_GAP
    }
}
