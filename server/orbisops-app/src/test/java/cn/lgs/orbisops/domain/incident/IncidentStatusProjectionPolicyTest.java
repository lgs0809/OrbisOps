package cn.lgs.orbisops.domain.incident;

import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.model.IncidentTimelineEntry;
import cn.lgs.orbisops.domain.incident.service.IncidentStatusProjectionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentStatusProjectionPolicyTest {

    private final IncidentStatusProjectionPolicy policy = new IncidentStatusProjectionPolicy();

    @Test
    void reopenedOccurrenceIgnoresClosedAndResolvedFactsFromPreviousOccurrence() {
        IncidentSnapshot incident = incident(IncidentStatus.OPEN);
        List<IncidentTimelineEntry> timeline = List.of(
                event("INVESTIGATION_STARTED"),
                event("INCIDENT_REOPENED"),
                event("INCIDENT_CLOSED"),
                event("VERIFICATION_SUCCEEDED"));

        IncidentStatus status = policy.project(incident, timeline, List.of(), List.of());

        assertEquals(IncidentStatus.INVESTIGATING, status);
    }

    @Test
    void latestVerificationAttemptWinsInsteadOfHistoricalSuccess() {
        IncidentSnapshot incident = incident(IncidentStatus.OPEN);
        List<IncidentTimelineEntry> timeline = List.of(
                event("VERIFICATION_FAILED"),
                event("VERIFICATION_STARTED"),
                event("VERIFICATION_SUCCEEDED"));

        IncidentStatus status = policy.project(incident, timeline, List.of(), List.of("LANDED"));

        assertEquals(IncidentStatus.ACTION_REQUIRED, status);
    }

    @Test
    void insufficientVerificationRemainsVerifying() {
        IncidentSnapshot incident = incident(IncidentStatus.OPEN);
        List<IncidentTimelineEntry> timeline = List.of(
                event("VERIFICATION_INSUFFICIENT"),
                event("VERIFICATION_STARTED"));

        IncidentStatus status = policy.project(incident, timeline, List.of(), List.of("LANDED"));

        assertEquals(IncidentStatus.VERIFYING, status);
    }

    private IncidentSnapshot incident(IncidentStatus status) {
        IncidentSnapshot incident = mock(IncidentSnapshot.class);
        when(incident.status()).thenReturn(status);
        return incident;
    }

    private IncidentTimelineEntry event(String type) {
        IncidentTimelineEntry entry = mock(IncidentTimelineEntry.class);
        when(entry.eventType()).thenReturn(type);
        return entry;
    }
}
