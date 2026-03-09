package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertAggregationApplicationService;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.alert.AlertOutboxApplicationService;
import cn.lgs.orbisops.application.alert.AlertRuleManagementApplicationService;
import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertEventMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertOutboxMapper;
import cn.lgs.orbisops.trigger.application.alert.OpsAlertRuleMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertTriggerServiceTest {

    @Test
    void keepsLegacyEightArgumentConstructorAvailable() {
        OpsAlertTriggerService service = new OpsAlertTriggerService(
                mock(OpsAnalysisRunService.class),
                mock(AlertRuleManagementApplicationService.class),
                mock(AlertEventApplicationService.class),
                mock(AlertAggregationApplicationService.class),
                mock(AlertOutboxApplicationService.class),
                new OpsAlertRuleMapper(),
                new OpsAlertEventMapper(),
                new OpsAlertOutboxMapper());

        assertNotNull(service);
    }

    @Test
    void facadeDelegatesPendingSummaryAndOutboxProcessing() {
        AlertAggregationApplicationService aggregation = mock(AlertAggregationApplicationService.class);
        OpsAlertWebhookProtocolService protocol = new OpsAlertWebhookProtocolService();
        OpsAlertRuleCatalog rules = mock(OpsAlertRuleCatalog.class);
        OpsAlertEventRecorder events = mock(OpsAlertEventRecorder.class);
        OpsAlertRunSubmissionCoordinator submissions = mock(OpsAlertRunSubmissionCoordinator.class);
        OpsAlertSummaryCoordinator summaries = mock(OpsAlertSummaryCoordinator.class);
        OpsAlertTriggerService service = new OpsAlertTriggerService(
                aggregation,
                protocol,
                rules,
                events,
                submissions,
                summaries,
                OpsAlertTriggerSettings.defaults());
        Function<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> analyzer = ignored -> null;
        when(summaries.enqueueDueSummaries(5)).thenReturn(2);
        when(submissions.processPending(5, 2, analyzer))
                .thenReturn(new AlertTriggerOutboxOutcome(3, 1, 0, 0, 0, 2));

        AlertTriggerOutboxOutcome result = service.processPendingOutbox(5, analyzer);

        assertEquals(1, result.submitted());
        assertEquals(2, result.summariesQueued());
        verify(summaries).enqueueDueSummaries(5);
        verify(submissions).processPending(5, 2, analyzer);
    }
}
