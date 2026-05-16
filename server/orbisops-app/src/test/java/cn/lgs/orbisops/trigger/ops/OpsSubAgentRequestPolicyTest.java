package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsSubAgentRequestPolicyTest {

    private final OpsSubAgentRequestPolicy policy = new OpsSubAgentRequestPolicy();

    @Test
    void rangeDecisionMustOnlyExpandAndApplyRecentLogDecision() {
        OpsAgentRunRequestDTO request = fullRequest(30, "10m", false);

        OpsAgentRunRequestDTO shorterCandidate = policy.applyLogDecision(request, 5, true);
        OpsAgentRunRequestDTO longerCandidate = policy.applyRangeDecision(request, 60);

        assertAll(
                () -> assertEquals(30, shorterCandidate.getRangeMinutes()),
                () -> assertTrue(shorterCandidate.getIncludeRecentLogs()),
                () -> assertEquals(60, longerCandidate.getRangeMinutes()),
                () -> assertFalse(longerCandidate.getIncludeRecentLogs()),
                () -> assertRunIdentityPreserved(request, shorterCandidate),
                () -> assertRunIdentityPreserved(request, longerCandidate));
    }

    @Test
    void retryRangeMustCapAtTwoHundredFortyMinutesAndPreserveCompleteRunContext() {
        OpsAgentRunRequestDTO request = fullRequest(200, "30m", false);

        OpsAgentRunRequestDTO expanded = policy.expandRange(request, true);
        OpsAgentRunRequestDTO expandedAgain = policy.expandRange(expanded, true);

        assertAll(
                () -> assertEquals(240, expanded.getRangeMinutes()),
                () -> assertEquals(240, expandedAgain.getRangeMinutes()),
                () -> assertTrue(expanded.getIncludeRecentLogs()),
                () -> assertRunIdentityPreserved(request, expanded),
                () -> assertRunIdentityPreserved(request, expandedAgain));
    }

    @Test
    void prometheusWindowMustFollowBoundedOrderAndNeverShrink() {
        assertAll(
                () -> assertEquals("3m", policy.expandPromWindow(fullRequest(15, "1m", true)).getPromWindow()),
                () -> assertEquals("5m", policy.expandPromWindow(fullRequest(15, "3m", true)).getPromWindow()),
                () -> assertEquals("10m", policy.expandPromWindow(fullRequest(15, "5m", true)).getPromWindow()),
                () -> assertEquals("15m", policy.expandPromWindow(fullRequest(15, "10m", true)).getPromWindow()),
                () -> assertEquals("30m", policy.expandPromWindow(fullRequest(15, "15m", true)).getPromWindow()),
                () -> assertEquals("1h", policy.expandPromWindow(fullRequest(15, "30m", true)).getPromWindow()),
                () -> assertEquals("1h", policy.expandPromWindow(fullRequest(15, "1h", true)).getPromWindow()),
                () -> assertEquals("30m", policy.applyPromWindowDecision(
                        fullRequest(15, "30m", true), "5m").getPromWindow()),
                () -> assertEquals("1h", policy.applyPromWindowDecision(
                        fullRequest(15, "30m", true), "1h").getPromWindow()));
    }

    private OpsAgentRunRequestDTO fullRequest(int rangeMinutes, String promWindow, boolean includeRecentLogs) {
        return OpsAgentRunRequestDTO.builder()
                .runId("run-427")
                .requestedBy("operator-a")
                .projectId("project-demo")
                .agentDefinitionId("agent-definition-1")
                .agentVersion(7)
                .agentDefinitionSnapshotJson("{\"version\":7}")
                .query("原始 query")
                .question("原始 question")
                .rangeMinutes(rangeMinutes)
                .promWindow(promWindow)
                .includeRecentLogs(includeRecentLogs)
                .maxRounds(6)
                .subAgentMaxIterations(4)
                .nodeTimeoutSeconds(77)
                .maxEvidenceItems(19)
                .notifyChannel(true)
                .notificationChannelId("channel-1")
                .notificationTarget("ops-room")
                .triggerSource("alert-webhook")
                .triggerEventId("event-99")
                .build();
    }

    private void assertRunIdentityPreserved(OpsAgentRunRequestDTO expected, OpsAgentRunRequestDTO actual) {
        assertAll(
                () -> assertEquals(expected.getRunId(), actual.getRunId()),
                () -> assertEquals(expected.getRequestedBy(), actual.getRequestedBy()),
                () -> assertEquals(expected.getProjectId(), actual.getProjectId()),
                () -> assertEquals(expected.getAgentDefinitionId(), actual.getAgentDefinitionId()),
                () -> assertEquals(expected.getAgentVersion(), actual.getAgentVersion()),
                () -> assertEquals(expected.getAgentDefinitionSnapshotJson(), actual.getAgentDefinitionSnapshotJson()),
                () -> assertEquals(expected.getQuery(), actual.getQuery()),
                () -> assertEquals(expected.getQuestion(), actual.getQuestion()),
                () -> assertEquals(expected.getMaxRounds(), actual.getMaxRounds()),
                () -> assertEquals(expected.getSubAgentMaxIterations(), actual.getSubAgentMaxIterations()),
                () -> assertEquals(expected.getNodeTimeoutSeconds(), actual.getNodeTimeoutSeconds()),
                () -> assertEquals(expected.getMaxEvidenceItems(), actual.getMaxEvidenceItems()),
                () -> assertEquals(expected.getNotifyChannel(), actual.getNotifyChannel()),
                () -> assertEquals(expected.getNotificationChannelId(), actual.getNotificationChannelId()),
                () -> assertEquals(expected.getNotificationTarget(), actual.getNotificationTarget()),
                () -> assertEquals(expected.getTriggerSource(), actual.getTriggerSource()),
                () -> assertEquals(expected.getTriggerEventId(), actual.getTriggerEventId()));
    }
}
