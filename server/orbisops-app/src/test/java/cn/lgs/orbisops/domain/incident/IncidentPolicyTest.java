package cn.lgs.orbisops.domain.incident;

import cn.lgs.orbisops.domain.incident.model.IncidentAlertDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.IncidentDraft;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.domain.incident.service.IncidentPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentPolicyTest {

    private final IncidentPolicy policy = new IncidentPolicy();

    @Test
    void manualIncidentAlwaysStartsOpen() {
        IncidentDraft draft = policy.manual(
                "incident_1",
                " project-a ",
                " database unavailable ",
                "",
                "warning",
                " payment ",
                "",
                " detail ",
                Map.of("team", "core"),
                Map.of(),
                List.of("mysql"));

        assertEquals("project-a", draft.projectId());
        assertEquals("database unavailable", draft.title());
        assertEquals(IncidentStatus.OPEN, draft.status());
        assertEquals("WARNING", draft.severity());
        assertEquals("MANUAL", draft.sourceType());
    }

    @Test
    void alertIdentityIsDeterministicAndProjectScoped() {
        IncidentAlertDraft first = policy.alert(signal("project-a", "rule-1:fingerprint-1", "TRIGGERED"));
        IncidentAlertDraft repeated = policy.alert(signal("project-a", "rule-1:fingerprint-1", "FAILED"));
        IncidentAlertDraft otherProject = policy.alert(signal("project-b", "rule-1:fingerprint-1", "TRIGGERED"));

        assertEquals(first.incidentId(), repeated.incidentId());
        assertEquals("project-a:rule-1:fingerprint-1", first.projectDedupKey());
        assertTrue(!first.incidentId().equals(otherProject.incidentId()));
    }

    @Test
    void recoverySignalIsExplicitDomainFact() {
        IncidentAlertDraft draft = policy.alert(signal("project-a", "rule-1", "RECOVERY_RESOLVED"));

        assertTrue(draft.recovery());
    }

    @Test
    void manualCreateCannotPredeclareFactProjectedStatus() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.manual(
                        "incident_1", "project-a", "database unavailable", "investigating",
                        "warning", "mysql", "manual", "detail", Map.of(), Map.of(), List.of()));

        assertEquals("INCIDENT_CREATE_STATUS_MUST_BE_OPEN", error.getMessage());
    }

    @Test
    void historicalStatusesRemainReadableWithoutExposingOldProductStates() {
        assertEquals(IncidentStatus.INVESTIGATING, IncidentStatus.fromStored("ACKED"));
        assertEquals(IncidentStatus.VERIFYING, IncidentStatus.fromStored("MITIGATED"));
        assertEquals(IncidentStatus.CLOSED, IncidentStatus.fromStored("REVIEWED"));
    }

    @Test
    void limitsAreClampedByDomainPolicy() {
        assertEquals(1, policy.incidentLimit(0));
        assertEquals(200, policy.incidentLimit(999));
        assertEquals(1, policy.timelineLimit(-1));
        assertEquals(300, policy.timelineLimit(999));
    }

    private IncidentAlertSignal signal(String projectId, String dedupKey, String status) {
        return new IncidentAlertSignal(
                42L,
                7L,
                "high error rate",
                projectId,
                "alertmanager",
                status,
                dedupKey,
                "fingerprint-1",
                "HighErrorRate",
                "warning",
                "payment",
                "run-1",
                "error rate high",
                "{}");
    }
}
