package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsInvestigationRetryServiceTest {

    private final OpsInvestigationRetryService service =
            new OpsInvestigationRetryService();

    @Test
    void ineligibleResultReturnsEmptyWithoutReadingMissingRange() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .rangeMinutes(null)
                .promWindow("5m")
                .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .status("FOUND")
                        .shouldRetry(false)
                        .build();

        assertTrue(service.adjustedRequest(request, result).isEmpty());
    }

    @Test
    void mapsDomainAdjustmentAndPreservesLegacyRetryRequestProjection() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .requestedBy("alice")
                .projectId("project-1")
                .agentDefinitionId("agent-def")
                .agentVersion(7)
                .agentDefinitionSnapshotJson("snapshot")
                .query("query")
                .question("question")
                .rangeMinutes(10)
                .promWindow("5m")
                .includeRecentLogs(true)
                .maxRounds(3)
                .subAgentMaxIterations(4)
                .nodeTimeoutSeconds(30)
                .maxEvidenceItems(9)
                .notifyChannel(true)
                .notificationChannelId("channel-1")
                .notificationTarget("target-1")
                .triggerSource("webhook")
                .triggerEventId("event-1")
                .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .status("INSUFFICIENT")
                        .shouldRetry(true)
                        .build();

        Optional<OpsAgentRunRequestDTO> adjusted =
                service.adjustedRequest(request, result);

        OpsAgentRunRequestDTO retry = adjusted.orElseThrow();
        assertEquals("run-1", retry.getRunId());
        assertEquals("agent-def", retry.getAgentDefinitionId());
        assertEquals("question", retry.getQuestion());
        assertEquals(40, retry.getRangeMinutes());
        assertEquals("15m", retry.getPromWindow());
        assertEquals(true, retry.getIncludeRecentLogs());
        assertEquals(0, retry.getMaxRounds());
        assertEquals(4, retry.getSubAgentMaxIterations());
        assertEquals(30, retry.getNodeTimeoutSeconds());
        assertEquals(9, retry.getMaxEvidenceItems());
        assertEquals(true, retry.getNotifyChannel());
        assertEquals("channel-1", retry.getNotificationChannelId());
        assertEquals("target-1", retry.getNotificationTarget());

        assertNull(retry.getRequestedBy());
        assertNull(retry.getProjectId());
        assertNull(retry.getAgentVersion());
        assertNull(retry.getAgentDefinitionSnapshotJson());
        assertNull(retry.getQuery());
        assertNull(retry.getTriggerSource());
        assertNull(retry.getTriggerEventId());
    }

    @Test
    void nullRequestOrResultReturnsEmpty() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .rangeMinutes(10)
                .build();
        OpsAnalysisResponseDTO.InvestigationResultDTO result =
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .status("INSUFFICIENT")
                        .shouldRetry(true)
                        .build();

        assertTrue(service.adjustedRequest(null, result).isEmpty());
        assertTrue(service.adjustedRequest(request, null).isEmpty());
    }
}
