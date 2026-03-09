package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisRunRequestNormalizerTest {

    @Test
    void shouldNormalizeBoundsFreezeSnapshotAndPreserveRunContext() {
        OpsAgentDefinitionQueryGateway gateway = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition definition = OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .projectId("payment")
                .version(7)
                .definitionHash("hash-v7")
                .name("Ops Agent")
                .engine("GRAPH")
                .build();
        when(gateway.resolveForProject("ops-agent", null, false, "payment"))
                .thenReturn(definition);
        OpsAnalysisRunRequestNormalizer normalizer = new OpsAnalysisRunRequestNormalizer(
                new OpsAnalysisAgentDefinitionSnapshotResolver(gateway));

        OpsAgentRunRequestDTO normalized = normalizer.normalize(OpsAgentRunRequestDTO.builder()
                .runId("run-1")
                .requestedBy("alice")
                .projectId("  payment  ")
                .agentDefinitionId("ops-agent")
                .query("  检查支付接口  ")
                .question("  ")
                .rangeMinutes(5000)
                .promWindow("bad-window")
                .includeRecentLogs(false)
                .maxRounds(8)
                .subAgentMaxIterations(0)
                .nodeTimeoutSeconds(500)
                .maxEvidenceItems(0)
                .changeRequested(true)
                .notifyChannel(true)
                .notificationChannelId("channel-1")
                .notificationTarget("ops-room")
                .triggerSource("alert")
                .executionStyle("WORKFLOW")
                .triggerEventId("event-9")
                .build());

        assertAll(
                () -> assertEquals("run-1", normalized.getRunId()),
                () -> assertEquals("alice", normalized.getRequestedBy()),
                () -> assertEquals("payment", normalized.getProjectId()),
                () -> assertEquals("ops-agent", normalized.getAgentDefinitionId()),
                () -> assertEquals(7, normalized.getAgentVersion()),
                () -> assertNotNull(normalized.getAgentDefinitionSnapshotJson()),
                () -> assertEquals("检查支付接口", normalized.getQuery()),
                () -> assertEquals("检查支付接口", normalized.getQuestion()),
                () -> assertEquals(1440, normalized.getRangeMinutes()),
                () -> assertEquals("5m", normalized.getPromWindow()),
                () -> assertFalse(normalized.getIncludeRecentLogs()),
                () -> assertEquals(8, normalized.getMaxRounds()),
                () -> assertEquals(1, normalized.getSubAgentMaxIterations()),
                () -> assertEquals(300, normalized.getNodeTimeoutSeconds()),
                () -> assertEquals(1, normalized.getMaxEvidenceItems()),
                () -> assertEquals(true, normalized.getChangeRequested()),
                () -> assertEquals(true, normalized.getNotifyChannel()),
                () -> assertEquals("channel-1", normalized.getNotificationChannelId()),
                () -> assertEquals("ops-room", normalized.getNotificationTarget()),
                () -> assertEquals("alert", normalized.getTriggerSource()),
                () -> assertEquals("WORKFLOW", normalized.getExecutionStyle()),
                () -> assertEquals("event-9", normalized.getTriggerEventId()));
    }

    @Test
    void shouldApplyStableDefaultsAndRequireProject() {
        OpsAgentDefinitionQueryGateway gateway = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAnalysisRunRequestNormalizer normalizer = new OpsAnalysisRunRequestNormalizer(
                new OpsAnalysisAgentDefinitionSnapshotResolver(gateway));

        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(new OpsAgentRunRequestDTO()));

        OpsAgentRunRequestDTO normalized = normalizer.normalize(OpsAgentRunRequestDTO.builder()
                .projectId("project-a")
                .question("检查状态")
                .build());
        assertAll(
                () -> assertEquals(15, normalized.getRangeMinutes()),
                () -> assertEquals("5m", normalized.getPromWindow()),
                () -> assertEquals(3, normalized.getSubAgentMaxIterations()),
                () -> assertEquals(true, normalized.getIncludeRecentLogs()),
                () -> assertEquals("检查状态", normalized.getQuery()),
                () -> assertEquals("检查状态", normalized.getQuestion()));
    }
}
