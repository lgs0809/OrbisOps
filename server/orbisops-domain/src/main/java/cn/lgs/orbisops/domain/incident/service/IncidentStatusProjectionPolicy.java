package cn.lgs.orbisops.domain.incident.service;

import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Projects the business Incident status from authoritative facts. It does not
 * own an execution state machine and never drives ToolExecution/Landing.
 */
public final class IncidentStatusProjectionPolicy {

    private static final Set<String> RUNNING = Set.of(
            "PENDING", "QUEUED", "RUNNING", "STARTED", "IN_PROGRESS");

    public IncidentStatus project(
            IncidentSnapshot incident,
            List<IncidentTimelineEntry> timeline,
            List<IncidentRunSnapshot> runs,
            List<String> changePackageStatuses) {
        if (incident == null) throw new IllegalArgumentException("INCIDENT_REQUIRED");
        List<IncidentTimelineEntry> facts = currentEpisode(timeline == null ? List.of() : timeline);
        List<IncidentRunSnapshot> agentRuns = runs == null ? List.of() : runs;
        List<String> changes = changePackageStatuses == null ? List.of() : changePackageStatuses;

        if (incident.status() == IncidentStatus.CLOSED || event(facts, "INCIDENT_CLOSED")) {
            return IncidentStatus.CLOSED;
        }
        String verification = latestVerificationFact(facts);
        if ("VERIFICATION_SUCCEEDED".equals(verification) || incident.status() == IncidentStatus.RESOLVED) {
            return IncidentStatus.RESOLVED;
        }
        if ("VERIFICATION_FAILED".equals(verification)) {
            return IncidentStatus.ACTION_REQUIRED;
        }
        boolean recoveryPending = event(facts, "RECOVERY_SIGNAL_RECEIVED") || hasChange(changes, "LANDED");
        if (recoveryPending && ("VERIFICATION_STARTED".equals(verification)
                || "VERIFICATION_INSUFFICIENT".equals(verification)
                || verification.isBlank())) {
            return IncidentStatus.VERIFYING;
        }
        if (hasChange(changes, "LANDING_RUNNING") || event(facts, "REMEDIATION_STARTED")) {
            return IncidentStatus.REMEDIATING;
        }
        if (requiresAction(changes)
                || event(facts, "DIAGNOSIS_ACTION_REQUIRED")
                || event(facts, "USER_ACTION_REQUIRED")) {
            return IncidentStatus.ACTION_REQUIRED;
        }
        if (event(facts, "ANALYSIS_LINKED")
                || event(facts, "INVESTIGATION_STARTED")
                || agentRuns.stream().anyMatch(this::active)) {
            return IncidentStatus.INVESTIGATING;
        }
        return IncidentStatus.OPEN;
    }

    private boolean requiresAction(List<String> statuses) {
        return statuses.stream()
                .map(this::normalize)
                .anyMatch(value -> Set.of(
                        "DRAFT", "VALIDATING", "VALIDATION_FAILED", "REVISING",
                        "READY_FOR_REVIEW", "REVIEWING", "REJECTED", "APPROVED",
                        "LANDING_FAILED", "NEEDS_REPLAN").contains(value));
    }

    private boolean hasChange(List<String> statuses, String expected) {
        return statuses.stream().map(this::normalize).anyMatch(expected::equals);
    }

    private boolean active(IncidentRunSnapshot run) {
        return run != null && RUNNING.contains(normalize(run.status()));
    }

    private boolean event(List<IncidentTimelineEntry> timeline, String expected) {
        return timeline.stream()
                .filter(item -> item != null)
                .map(IncidentTimelineEntry::eventType)
                .map(this::normalize)
                .anyMatch(expected::equals);
    }

    private String latestVerificationFact(List<IncidentTimelineEntry> timeline) {
        Set<String> verificationEvents = Set.of(
                "VERIFICATION_SUCCEEDED",
                "VERIFICATION_FAILED",
                "VERIFICATION_INSUFFICIENT",
                "VERIFICATION_STARTED");
        return timeline.stream()
                .filter(item -> item != null)
                .map(IncidentTimelineEntry::eventType)
                .map(this::normalize)
                .filter(verificationEvents::contains)
                .findFirst()
                .orElse("");
    }

    /**
     * Timeline is read newest-first. A reopen starts a new occurrence; resolved/closed
     * facts from an older occurrence must not decide the current Incident status.
     */
    private List<IncidentTimelineEntry> currentEpisode(List<IncidentTimelineEntry> timeline) {
        if (timeline.isEmpty()) return List.of();
        List<IncidentTimelineEntry> current = new ArrayList<>();
        for (IncidentTimelineEntry item : timeline) {
            if (item == null) continue;
            current.add(item);
            if ("INCIDENT_REOPENED".equals(normalize(item.eventType()))) {
                break;
            }
        }
        return List.copyOf(current);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
