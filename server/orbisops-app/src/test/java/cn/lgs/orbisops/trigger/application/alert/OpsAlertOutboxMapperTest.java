package cn.lgs.orbisops.trigger.application.alert;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.alert.AlertOutboxBatchResult;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.domain.alert.model.AlertRunRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsAlertOutboxMapperTest {

    private final OpsAlertOutboxMapper mapper = new OpsAlertOutboxMapper();

    @Test
    void mapsDtoToTypedDraftAndBackWithoutLosingRuntimeFields() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-request-1")
                .requestedBy("alertmanager:rule-7")
                .projectId("project-a")
                .agentDefinitionId("agent-a")
                .agentVersion(3)
                .agentDefinitionSnapshotJson("{}")
                .query("query")
                .question("analyse alert")
                .rangeMinutes(30)
                .promWindow("5m")
                .includeRecentLogs(true)
                .maxRounds(4)
                .subAgentMaxIterations(5)
                .nodeTimeoutSeconds(120)
                .maxEvidenceItems(20)
                .notifyChannel(true)
                .notificationChannelId("channel-a")
                .notificationTarget("ops")
                .executionStyle("WORKFLOW")
                .triggerSource("ALERTMANAGER")
                .triggerEventId("fingerprint-1")
                .build();

        AlertOutboxDraft draft = mapper.draft(
                "dispatch-1", 7L, "project-a", "fingerprint-1", "aggregate-1",
                "RECOVERY", 80, request, Map.of("status", "resolved"));
        OpsAgentRunRequestDTO restored = mapper.request(draft.request());

        assertEquals(AlertAggregateEventType.RECOVERY, draft.eventType());
        assertEquals(80, draft.priority());
        assertEquals("agent-a", restored.getAgentDefinitionId());
        assertEquals(3, restored.getAgentVersion());
        assertEquals(4, restored.getMaxRounds());
        assertEquals("channel-a", restored.getNotificationChannelId());
        assertEquals("WORKFLOW", restored.getExecutionStyle());
        assertEquals("fingerprint-1", restored.getTriggerEventId());
    }

    @Test
    void mapsTypedRunRequestToApiDto() {
        AlertRunRequest request = new AlertRunRequest(
                "", "alertmanager:rule-7", "project-a", "agent-a", 3, "{}",
                "", "analyse alert", 30, "5m", true, null, 5, 120, 20,
                false, "", "", "WORKFLOW", "ALERTMANAGER", "fingerprint-1");

        OpsAgentRunRequestDTO dto = mapper.request(request);

        assertEquals("project-a", dto.getProjectId());
        assertEquals("agent-a", dto.getAgentDefinitionId());
        assertEquals("analyse alert", dto.getQuestion());
        assertEquals("WORKFLOW", dto.getExecutionStyle());
    }

    @Test
    void mapsBatchResultToTypedExistingResponseShape() {
        AlertTriggerOutboxOutcome response = mapper.batchResult(
                new AlertOutboxBatchResult(10, 6, 2, 1, 3), 4);

        assertEquals(10, response.scanned());
        assertEquals(6, response.submitted());
        assertEquals(2, response.failed());
        assertEquals(1, response.recovered());
        assertEquals(3, response.deadLettered());
        assertEquals(4, response.summariesQueued());
        assertTrue(response.hasActivity());
    }
}
