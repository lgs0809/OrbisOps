package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.alert.AlertTriggerOutboxOutcome;
import cn.lgs.orbisops.trigger.application.ops.OpsAnalysisApplicationService;
import cn.lgs.orbisops.trigger.ops.OpsAlertTriggerService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAlertTriggerOutboxJobTest {

    @Test
    void legacyConstructorKeepsScheduledProcessingDisabled() {
        OpsAlertTriggerService triggers = mock(OpsAlertTriggerService.class);
        OpsAlertTriggerOutboxJob job = new OpsAlertTriggerOutboxJob(
                triggers,
                mock(OpsAnalysisApplicationService.class));

        job.process();

        verify(triggers, never()).processPendingOutbox(
                org.mockito.ArgumentMatchers.anyInt(),
                any());
    }

    @Test
    void enabledJobUsesTypedBatchSizeAndOutcome() {
        OpsAlertTriggerService triggers = mock(OpsAlertTriggerService.class);
        OpsAnalysisApplicationService analysis = mock(OpsAnalysisApplicationService.class);
        when(triggers.processPendingOutbox(eq(32), any()))
                .thenReturn(new AlertTriggerOutboxOutcome(2, 1, 0, 0, 0, 1));
        OpsAlertTriggerOutboxJob job = new OpsAlertTriggerOutboxJob(
                triggers,
                analysis,
                new OpsAlertOutboxJobSettings(true, 32));

        job.process();

        verify(triggers).processPendingOutbox(eq(32), any());
    }
}
