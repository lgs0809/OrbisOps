package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.incident.AppendIncidentTimelineCommand;
import cn.lgs.orbisops.application.incident.CreateIncidentCommand;
import cn.lgs.orbisops.domain.incident.model.IncidentAlertSignal;
import cn.lgs.orbisops.domain.incident.model.IncidentRunSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.model.IncidentStatus;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerEvent;
import cn.lgs.orbisops.trigger.ops.OpsIncident;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsIncidentMapperTest {

    private final OpsIncidentMapper mapper = new OpsIncidentMapper();

    @Test
    void requestMapsToTypedCommandWithoutTrustingActorField() {
        CreateIncidentCommand command = mapper.createCommand(Map.of(
                "projectId", " project-a ",
                "title", " database unavailable ",
                "actor", "forged-user",
                "labels", Map.of("team", "core"),
                "affectedResources", List.of("mysql")));

        assertEquals("project-a", command.projectId());
        assertEquals("database unavailable", command.title());
        assertEquals(Map.of("team", "core"), command.labels());
        assertEquals(List.of("mysql"), command.affectedResources());
    }

    @Test
    void timelinePayloadIsMappedButActorIsOverwrittenByApplicationService() {
        AppendIncidentTimelineCommand command = mapper.timelineCommand(Map.of(
                "title", "manual note",
                "actor", "forged-user"));

        assertEquals("manual note", command.title());
        assertEquals("forged-user", command.payload().get("actor"));
    }

    @Test
    void alertEventMapsToTypedSignal() {
        IncidentAlertSignal signal = mapper.alertSignal(OpsAlertTriggerEvent.builder()
                .id(42L)
                .projectId("project-a")
                .status("RECOVERY_RESOLVED")
                .dedupKey("rule-1:fingerprint-1")
                .build());

        assertEquals("project-a", signal.projectId());
        assertEquals("rule-1:fingerprint-1", signal.dedupKey());
        assertEquals("RECOVERY_RESOLVED", signal.eventStatus());
    }

    @Test
    void typedSnapshotAndRunsMapToExistingHttpContract() {
        IncidentSnapshot snapshot = new IncidentSnapshot(
                1L,
                "incident-1",
                "project-a",
                "database unavailable",
                IncidentStatus.OPEN,
                "WARN",
                "mysql",
                "MANUAL",
                "fingerprint",
                "dedup",
                "run-1",
                "user-a",
                "detail",
                "{}",
                "{}",
                2,
                "[]",
                "first", "last", "created", "updated", "", "", "");

        OpsIncident response = mapper.incident(snapshot);
        List<Map<String, Object>> runs = mapper.runs(List.of(new IncidentRunSnapshot(
                "run-1", "SUCCEEDED", "", "created", "updated", 10L)));

        assertEquals("OPEN", response.getStatus());
        assertEquals("user-a", response.getOwnerUserId());
        assertEquals(2L, response.getOccurrenceCount());
        assertEquals("run-1", runs.get(0).get("run_id"));
        assertFalse(runs.get(0).containsKey("runId"));
    }
}
