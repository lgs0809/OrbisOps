package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAnalysisRunAlertOutcomeReporterTest {

    @Test
    void shouldMapTypedOutcomeAndNormalizeBoundedSummary() {
        AlertEventApplicationService alertEvents = mock(AlertEventApplicationService.class);
        OpsAnalysisRunAlertOutcomeReporter reporter =
                new OpsAnalysisRunAlertOutcomeReporter(alertEvents);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .triggerSource("ALERTMANAGER")
                .triggerEventId("event-1")
                .build();
        String report = "  first\n\tsecond  " + "x".repeat(600);
        String summary = reporter.summarize(OpsAnalysisResponseDTO.builder()
                .markdownReport(report)
                .build());

        reporter.report(request, "run-1", "SUCCEEDED", summary, null);

        ArgumentCaptor<AlertRunOutcome> outcome = ArgumentCaptor.forClass(AlertRunOutcome.class);
        verify(alertEvents).updateRunOutcome(outcome.capture());
        assertAll(
                () -> assertEquals("ALERTMANAGER", outcome.getValue().sourceType()),
                () -> assertEquals("event-1", outcome.getValue().triggerEventId()),
                () -> assertEquals("run-1", outcome.getValue().runId()),
                () -> assertEquals("SUCCEEDED", outcome.getValue().runStatus()),
                () -> assertEquals(500, outcome.getValue().finalSummary().length()),
                () -> assertTrue(outcome.getValue().finalSummary().startsWith("first second")),
                () -> assertEquals("", reporter.summarize(null)),
                () -> assertEquals("", reporter.summarize(new OpsAnalysisResponseDTO())));
    }

    @Test
    void alertWriteFailureMustRemainIsolatedFromRunLifecycle() {
        AlertEventApplicationService alertEvents = mock(AlertEventApplicationService.class);
        doThrow(new IllegalStateException("alert store down"))
                .when(alertEvents).updateRunOutcome(any(AlertRunOutcome.class));
        OpsAnalysisRunAlertOutcomeReporter reporter =
                new OpsAnalysisRunAlertOutcomeReporter(alertEvents);

        assertDoesNotThrow(() -> reporter.report(
                OpsAgentRunRequestDTO.builder().triggerEventId("event-2").build(),
                "run-2",
                "FAILED",
                null,
                "analysis failed"));
    }
}
