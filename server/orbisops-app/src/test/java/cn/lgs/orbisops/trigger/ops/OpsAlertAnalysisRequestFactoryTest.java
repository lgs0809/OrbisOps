package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertAnalysisRequestFactoryTest {

    private final OpsAlertAnalysisRequestFactory factory =
            new OpsAlertAnalysisRequestFactory();
    private final OpsAlertWebhookProtocolService webhookProtocol =
            new OpsAlertWebhookProtocolService(() -> 1_700_000_000L);

    @Test
    void buildsLatestPublishedVersionBoundRequestAndDefaultQuestion() {
        OpsAgentDefinitionQueryGateway agentDefinitions =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(agentDefinitions.resolveForProject(
                "payment-agent",
                null,
                false,
                "payment"))
                .thenReturn(agent("payment-agent", "payment", 4, "hash-v4"));
        OpsAlertTriggerRule rule = ruleBuilder()
                .id(null)
                .agentBindingMode(" latest_published ")
                .build();
        OpsAlertWebhookProtocolService.AlertView alert = webhookProtocol.alertView(Map.of(
                "fingerprint", "fp-1",
                "status", "resolved",
                "labels", Map.of(
                        "alertname", "HighErrorRate",
                        "severity", "critical",
                        "service", "payment"),
                "annotations", Map.of(
                        "summary", "5xx increased",
                        "description", "checkout failed")));

        OpsAgentRunRequestDTO request = factory.build(
                agentDefinitions,
                rule,
                alert,
                "RECOVERY",
                0,
                "ALERTMANAGER");

        assertEquals("payment", request.getProjectId());
        assertEquals("alertmanager:rule-0", request.getRequestedBy());
        assertEquals(15, request.getRangeMinutes());
        assertEquals("1m", request.getPromWindow());
        assertTrue(request.getIncludeRecentLogs());
        assertEquals("payment-agent", request.getAgentDefinitionId());
        assertEquals(4, request.getAgentVersion());
        assertEquals("REACT", request.getExecutionStyle());
        assertTrue(request.getAgentDefinitionSnapshotJson().contains("hash-v4"));
        assertEquals(6, request.getSubAgentMaxIterations());
        assertEquals(90, request.getNodeTimeoutSeconds());
        assertEquals(30, request.getMaxEvidenceItems());
        assertEquals(Boolean.TRUE, request.getNotifyChannel());
        assertEquals("dingtalk", request.getNotificationChannelId());
        assertEquals("ops-room", request.getNotificationTarget());
        assertEquals("ALERTMANAGER", request.getTriggerSource());
        assertEquals("fp-1", request.getTriggerEventId());
        assertTrue(request.getQuestion().contains("HighErrorRate"));
        assertTrue(request.getQuestion().contains("critical"));
        assertTrue(request.getQuestion().contains("payment"));
        assertTrue(request.getQuestion().contains("resolved / RECOVERY"));
        assertTrue(request.getQuestion().contains("聚合出现次数：1"));
        assertTrue(request.getQuestion().contains("5xx increased"));
        assertTrue(request.getQuestion().contains("checkout failed"));
        verify(agentDefinitions).resolveForProject(
                "payment-agent",
                null,
                false,
                "payment");
    }

    @Test
    void buildsPinnedRequestAndRendersCustomTemplate() {
        OpsAgentDefinitionQueryGateway agentDefinitions =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(agentDefinitions.resolveForProject(
                "payment-agent",
                3,
                false,
                "payment"))
                .thenReturn(agent("payment-agent", "payment", 3, "hash-v3"));
        OpsAlertTriggerRule rule = ruleBuilder()
                .id(7L)
                .agentBindingMode("PINNED_VERSION")
                .agentVersion(3)
                .agentDefinitionHash("hash-v3")
                .questionTemplate("${alertName}|${severity}|${service}|${alertStatus}|${eventType}|${occurrenceCount}|${summary}|${description}|${labels}|${annotations}")
                .build();
        OpsAlertWebhookProtocolService.AlertView alert = webhookProtocol.alertView(Map.of(
                "fingerprint", "fp-pinned",
                "status", "firing",
                "labels", Map.of(
                        "alertname", "HighLatency",
                        "severity", "warning",
                        "service", "payment"),
                "annotations", Map.of(
                        "summary", "latency high",
                        "description", "p99 > 2s")));

        OpsAgentRunRequestDTO request = factory.build(
                agentDefinitions,
                rule,
                alert,
                "SUMMARY",
                5,
                "ALERTMANAGER");

        assertEquals("alertmanager:rule-7", request.getRequestedBy());
        assertEquals(3, request.getAgentVersion());
        assertTrue(request.getAgentDefinitionSnapshotJson().contains("hash-v3"));
        assertTrue(request.getQuestion().startsWith(
                "HighLatency|warning|payment|firing|SUMMARY|5|latency high|p99 > 2s|"));
        assertTrue(request.getQuestion().contains("\"alertname\":\"HighLatency\""));
        assertTrue(request.getQuestion().contains("\"summary\":\"latency high\""));
        verify(agentDefinitions).resolveForProject(
                "payment-agent",
                3,
                false,
                "payment");
    }

    @Test
    void specializedWorkflowAlertRunsAsWorkflow() {
        OpsAgentDefinitionQueryGateway agentDefinitions =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(agentDefinitions.resolveForProject(
                "payment-agent",
                3,
                false,
                "payment"))
                .thenReturn(agent("payment-agent", "payment", 3, "hash-v3", "SPECIALIZED_WORKFLOW"));
        OpsAlertTriggerRule rule = ruleBuilder()
                .id(7L)
                .agentBindingMode("PINNED_VERSION")
                .agentVersion(3)
                .agentDefinitionHash("hash-v3")
                .build();
        OpsAlertWebhookProtocolService.AlertView alert = webhookProtocol.alertView(Map.of(
                "fingerprint", "fp-workflow",
                "status", "firing",
                "labels", Map.of(
                        "alertname", "QueueBacklog",
                        "severity", "critical",
                        "service", "payment")));

        OpsAgentRunRequestDTO request = factory.build(
                agentDefinitions,
                rule,
                alert,
                "FIRST",
                1,
                "ALERTMANAGER");

        assertEquals("WORKFLOW", request.getExecutionStyle());
        assertEquals("payment-agent", request.getAgentDefinitionId());
        assertEquals(3, request.getAgentVersion());
    }

    @Test
    void workflowInputRetainsEventTimeAndTypedDataWithoutInterpretingAlertContents() {
        var registry = mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject("payment-agent", null, false, "payment"))
                .thenReturn(agent("payment-agent", "payment", 3, "hash-v3", "SPECIALIZED_WORKFLOW"));
        var rule = ruleBuilder().questionTemplate("""
                {"projectId":"${projectId}","serviceId":"${service}","alertTime":"${startsAt}",
                 "occurrenceCount":"${occurrenceCount}","labels":"${labels}",
                 "summary":"${summary}","detail":"Alert: ${description}","nested":["${eventType}",true]}
                """).build();
        String incoming = "quoted \"},\"projectId\":\"other\" / ${projectId} / $1 \\ tail\nnext";
        var alert = webhookProtocol.alertView(Map.of(
                "fingerprint", "fp-json", "startsAt", "2026-09-08T18:33:00Z",
                "labels", Map.of("service", "payment", "projectId", "untrusted-project"),
                "annotations", Map.of("summary", incoming, "description", incoming)));

        var request = factory.build(registry, rule, alert, "FIRST", 5, "ALERTMANAGER");
        var parsed = new cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy()
                .parseObject(request.getQuestion());

        assertEquals("WORKFLOW", request.getExecutionStyle());
        assertEquals("payment", request.getProjectId());
        assertEquals("payment", parsed.get("projectId"));
        assertEquals("2026-09-08T18:33:00Z", parsed.get("alertTime"));
        assertEquals(5, ((Number) parsed.get("occurrenceCount")).intValue());
        assertEquals(alert.labels(), parsed.get("labels"));
        assertEquals(incoming, parsed.get("summary"));
        assertEquals("Alert: " + incoming, parsed.get("detail"));
        assertEquals(java.util.List.of("FIRST", true), parsed.get("nested"));
        assertEquals("fp-json", request.getTriggerEventId());
    }

    @Test
    void invalidWorkflowInputFailsBeforeSubmissionInsteadOfFallingBackToFreeText() {
        var registry = mock(OpsAgentDefinitionQueryGateway.class);
        when(registry.resolveForProject("payment-agent", null, false, "payment"))
                .thenReturn(agent("payment-agent", "payment", 3, "hash-v3", "SPECIALIZED_WORKFLOW"));
        var alert = webhookProtocol.alertView(Map.of("labels", Map.of("service", "payment")));
        for (String template : java.util.List.of("{broken", "{\"time\":\"${startAt}\"}")) {
            assertThrows(IllegalArgumentException.class, () -> factory.build(registry,
                    ruleBuilder().questionTemplate(template).build(), alert, "FIRST", 1, "ALERTMANAGER"));
        }
        var oversized = webhookProtocol.alertView(Map.of(
                "annotations", Map.of("summary", "x".repeat(1_048_577))));
        assertThrows(IllegalArgumentException.class, () -> factory.build(registry,
                ruleBuilder().questionTemplate("{\"summary\":\"${summary}\"}").build(),
                oversized, "FIRST", 1, "ALERTMANAGER"));
    }

    @Test
    void rejectsInvalidBindingRegistryVersionAndPinnedHash() {
        OpsAlertWebhookProtocolService.AlertView alert = webhookProtocol.alertView(Map.of(
                "fingerprint", "fp",
                "labels", Map.of("alertname", "Alert")));

        assertEquals(
                "ALERT_AGENT_REGISTRY_UNAVAILABLE",
                assertThrows(
                        IllegalStateException.class,
                        () -> factory.build(
                                null,
                                ruleBuilder().build(),
                                alert,
                                "FIRST",
                                1,
                                "ALERTMANAGER"))
                        .getMessage());
        assertEquals(
                "Agent 绑定模式只允许 LATEST_PUBLISHED 或 PINNED_VERSION",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> factory.build(
                                mock(OpsAgentDefinitionQueryGateway.class),
                                ruleBuilder().agentBindingMode("rolling").build(),
                                alert,
                                "FIRST",
                                1,
                                "ALERTMANAGER"))
                        .getMessage());
        assertEquals(
                "PINNED_VERSION 告警规则必须选择 Agent 版本",
                assertThrows(
                        IllegalArgumentException.class,
                        () -> factory.build(
                                mock(OpsAgentDefinitionQueryGateway.class),
                                ruleBuilder()
                                        .agentBindingMode("PINNED_VERSION")
                                        .agentVersion(0)
                                        .build(),
                                alert,
                                "FIRST",
                                1,
                                "ALERTMANAGER"))
                        .getMessage());

        OpsAgentDefinitionQueryGateway incomplete =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(incomplete.resolveForProject(
                "payment-agent",
                null,
                false,
                "payment"))
                .thenReturn(agent("payment-agent", "payment", 0, ""));
        assertEquals(
                "ALERT_AGENT_VERSION_INCOMPLETE",
                assertThrows(
                        IllegalStateException.class,
                        () -> factory.build(
                                incomplete,
                                ruleBuilder().build(),
                                alert,
                                "FIRST",
                                1,
                                "ALERTMANAGER"))
                        .getMessage());

        OpsAgentDefinitionQueryGateway mismatch =
                mock(OpsAgentDefinitionQueryGateway.class);
        when(mismatch.resolveForProject(
                "payment-agent",
                3,
                false,
                "payment"))
                .thenReturn(agent("payment-agent", "payment", 3, "actual-hash"));
        assertEquals(
                "ALERT_AGENT_DEFINITION_HASH_MISMATCH",
                assertThrows(
                        SecurityException.class,
                        () -> factory.build(
                                mismatch,
                                ruleBuilder()
                                        .agentBindingMode("PINNED_VERSION")
                                        .agentVersion(3)
                                        .agentDefinitionHash("expected-hash")
                                        .build(),
                                alert,
                                "FIRST",
                                1,
                                "ALERTMANAGER"))
                        .getMessage());
    }

    private OpsAlertTriggerRule.OpsAlertTriggerRuleBuilder ruleBuilder() {
        return OpsAlertTriggerRule.builder()
                .projectId("payment")
                .agentDefinitionId("payment-agent")
                .agentBindingMode("LATEST_PUBLISHED")
                .rangeMinutes(15)
                .promWindow("1m")
                .includeRecentLogs(true)
                .subAgentMaxIterations(6)
                .nodeTimeoutSeconds(90)
                .maxEvidenceItems(30)
                .notifyChannel(true)
                .notificationChannelId("dingtalk")
                .notificationTarget("ops-room");
    }

    private OpsAgentDefinition agent(
            String agentId,
            String projectId,
            int version,
            String definitionHash) {
        return agent(agentId, projectId, version, definitionHash, "MAIN_ASSISTANT");
    }

    private OpsAgentDefinition agent(
            String agentId,
            String projectId,
            int version,
            String definitionHash,
            String definitionKind) {
        return OpsAgentDefinition.builder()
                .agentId(agentId)
                .projectId(projectId)
                .version(version)
                .definitionHash(definitionHash)
                .definitionKind(definitionKind)
                .build();
    }
}
