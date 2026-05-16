package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsRuntimeToolContributionSupportTest {

    @Test
    void canonicalChatProjectionMustPreserveTypedDiagnosisControls() {
        OpsAgentChatRequest chat = OpsAgentChatRequest.builder()
                .runId("chat-run-1")
                .userId("user-1")
                .projectId("demo-project")
                .agentDefinitionId("demo-ops-agent")
                .agentVersion(8)
                .query("verify service")
                .mode("AGENT")
                .rangeMinutes(30)
                .promWindow("5m")
                .includeRecentLogs(false)
                .maxRounds(3)
                .subAgentMaxIterations(4)
                .nodeTimeoutSeconds(90)
                .maxEvidenceItems(12)
                .changeRequested(true)
                .notifyChannel(true)
                .notificationChannelId("channel-1")
                .notificationTarget("ops")
                .build();

        OpsAgentRunRequestDTO projected = OpsRuntimeToolContributionSupport.analysisRequest(chat);

        assertEquals("chat-run-1", projected.getRunId());
        assertEquals("demo-project", projected.getProjectId());
        assertEquals("verify service", projected.getQuery());
        assertEquals(30, projected.getRangeMinutes());
        assertEquals("5m", projected.getPromWindow());
        assertFalse(projected.getIncludeRecentLogs());
        assertEquals(3, projected.getMaxRounds());
        assertEquals(4, projected.getSubAgentMaxIterations());
        assertEquals(90, projected.getNodeTimeoutSeconds());
        assertEquals(12, projected.getMaxEvidenceItems());
        assertTrue(projected.getChangeRequested());
        assertTrue(projected.getNotifyChannel());
        assertEquals("channel-1", projected.getNotificationChannelId());
        assertEquals("ops", projected.getNotificationTarget());
        assertEquals("AGENT", projected.getExecutionStyle());
    }
}
