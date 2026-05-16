package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.alert.AlertOutboxApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertAggregateEventType;
import cn.lgs.orbisops.domain.alert.model.AlertAggregationDecision;
import cn.lgs.orbisops.domain.alert.model.AlertOutboxDraft;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertOutboxMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertRunSubmissionCoordinatorTest {

    @Test
    void usesTypedQueueAndDispatchLimitsWithoutRebuildingProtocolInFacade() {
        OpsAnalysisRunService analysisRuns = mock(OpsAnalysisRunService.class);
        AlertOutboxApplicationService outbox = mock(AlertOutboxApplicationService.class);
        OpsAlertOutboxMapper mapper = mock(OpsAlertOutboxMapper.class);
        OpsAlertAnalysisRequestFactory requestFactory = mock(OpsAlertAnalysisRequestFactory.class);
        OpsAgentDefinitionQueryGateway agentDefinitions = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAlertTriggerSettings settings = new OpsAlertTriggerSettings(
                600, 11, 240, 90, 1_200, 321, 7);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .projectId("payment")
                .build();
        AlertOutboxDraft draft = mock(AlertOutboxDraft.class);
        when(requestFactory.build(
                eq(agentDefinitions),
                any(OpsAlertTriggerRule.class),
                any(OpsAlertWebhookProtocolService.AlertView.class),
                anyString(),
                anyLong(),
                eq("ALERTMANAGER"))).thenReturn(request);
        when(mapper.draft(
                anyString(),
                anyLong(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                eq(request),
                any())).thenReturn(draft);
        when(outbox.dispatchIfCapacity(
                anyString(), anyInt(), anyString(), anyInt(), any()))
                .thenReturn(Optional.of("run-9"));
        OpsAlertRunSubmissionCoordinator coordinator = new OpsAlertRunSubmissionCoordinator(
                analysisRuns,
                outbox,
                mapper,
                requestFactory,
                agentDefinitions,
                settings,
                "ALERTMANAGER");
        OpsAlertTriggerRule rule = OpsAlertTriggerRule.builder()
                .id(8L)
                .projectId("payment")
                .build();
        OpsAlertWebhookProtocolService.AlertView alert =
                new OpsAlertWebhookProtocolService().alertView(Map.of(
                        "fingerprint", "fp-8",
                        "labels", Map.of("alertname", "HighLatency")));
        AlertAggregationDecision decision = new AlertAggregationDecision(
                "aggregate-1",
                "dispatch-1",
                AlertAggregateEventType.FIRST,
                true,
                5,
                2,
                0,
                1,
                List.of("payment"));

        OpsAlertRunSubmissionCoordinator.SubmissionOutcome outcome =
                coordinator.enqueueAndDispatch(rule, alert, decision, Map.of(), ignored -> null);

        assertTrue(outcome.triggered());
        assertEquals("run-9", outcome.runId());
        verify(outbox).enqueue(draft, 321);
        verify(outbox).dispatchIfCapacity(
                eq("payment"), eq(7), eq("dispatch-1"), eq(11), any());
    }
}
