package cn.lgs.orbisops.application.schedule;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

class ScheduledTaskExecutionUseCaseTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"WAITING_APPROVAL", "CANCELED", "FAILED", "UNKNOWN", ""})
    void nonSuccessfulRuntimeMustNeverBecomeSuccessfulInspection(String status) {
        var catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        var command = command("payload");
        when(catalog.create(command)).thenReturn(42L);
        when(analysis.prepare(command, 42L)).thenReturn("request");
        when(analysis.execute("request")).thenReturn("response");
        when(analysis.runtimeStatus("response")).thenReturn(status);
        when(analysis.renderOutput("response")).thenReturn("evidence retained");
        new ScheduledTaskExecutionUseCase<>(catalog, Runnable::run, analysis).submit(command);
        verify(catalog, never()).markSucceeded(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        if (status.equals("WAITING_APPROVAL") || status.equals("CANCELED")) {
            verify(catalog).markIncomplete(42L, status, "evidence retained");
        } else {
            verify(catalog).markFailed(42L, "SCHEDULED_ANALYSIS_NOT_SUCCEEDED", "evidence retained");
        }
    }

    @Test
    void submitCreatesRecordThenExecutesInputAnalysisAndSuccessLifecycle() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        ScheduledTaskExecutionExecutorPort executor = Runnable::run;
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog, executor, analysis);
        ScheduledTaskExecutionCommand command = command("payload");
        when(catalog.create(command)).thenReturn(42L);
        when(analysis.prepare(command, 42L)).thenReturn("request");
        when(analysis.serializeInput("request")).thenReturn("input-json");
        when(analysis.execute("request")).thenReturn("response");
        when(analysis.runtimeStatus("response")).thenReturn("SUCCEEDED");
        when(analysis.renderOutput("response")).thenReturn("report");

        assertEquals(42L, useCase.submit(command));

        InOrder order = inOrder(catalog, analysis);
        order.verify(catalog).create(command);
        order.verify(analysis).prepare(command, 42L);
        order.verify(analysis).serializeInput("request");
        order.verify(catalog).updateInput(42L, "input-json");
        order.verify(analysis).execute("request");
        order.verify(analysis).renderOutput("response");
        order.verify(catalog).markSucceeded(42L, "report");
        verify(catalog, never()).markFailed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void analysisFailureMarksExecutionFailedWithoutEscapingAsyncTask() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        ScheduledTaskExecutionExecutorPort executor = Runnable::run;
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog, executor, analysis);
        ScheduledTaskExecutionCommand command = command("legacy-text");
        when(catalog.create(command)).thenReturn(42L);
        when(analysis.prepare(command, 42L))
                .thenThrow(new IllegalStateException("周期任务仍使用旧版文本配置，请重新创建任务"));

        assertEquals(42L, useCase.submit(command));

        verify(catalog).markFailed(
                42L,
                "周期任务仍使用旧版文本配置，请重新创建任务",
                null);
        verify(catalog, never()).markSucceeded(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executorRejectionMarksCreatedExecutionFailedThenRethrows() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        ScheduledTaskExecutionExecutorPort executor = task -> {
            throw new IllegalStateException("rejected");
        };
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog, executor, analysis);
        ScheduledTaskExecutionCommand command = command("payload");
        when(catalog.create(command)).thenReturn(42L);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> useCase.submit(command));

        assertEquals("rejected", error.getMessage());
        verify(catalog).markFailed(42L, "任务执行队列已满：rejected", null);
        verify(analysis, never()).prepare(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void healthyLightweightScreeningSkipsDeepAgent() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        ScheduledTaskScreeningPort screening = command -> new ScheduledTaskScreeningResult(
                true, "轻量检查正常", List.of());
        ScheduledTaskIncidentPort incidents = mock(ScheduledTaskIncidentPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog, Runnable::run, analysis, screening, incidents);
        ScheduledTaskExecutionCommand command = command("payload");
        when(catalog.create(command)).thenReturn(42L);

        assertEquals(42L, useCase.submit(command));

        verify(catalog).markSucceeded(42L, "轻量检查正常");
        verify(analysis, never()).prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(incidents, never()).openForAnomaly(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anomalousLightweightScreeningCreatesIncidentThenRunsDeepAgent() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        @SuppressWarnings("unchecked")
        ScheduledTaskAnalysisPort<String, String> analysis = mock(ScheduledTaskAnalysisPort.class);
        ScheduledTaskScreeningPort screening = command -> new ScheduledTaskScreeningResult(
                false, "Prometheus 异常", List.of("prometheus"));
        ScheduledTaskIncidentPort incidents = mock(ScheduledTaskIncidentPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog, Runnable::run, analysis, screening, incidents);
        ScheduledTaskExecutionCommand command = command("payload");
        when(catalog.create(command)).thenReturn(42L);
        when(incidents.openForAnomaly(org.mockito.ArgumentMatchers.eq(command), org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any()))
                .thenReturn("incident-1");
        when(analysis.prepare(command, 42L)).thenReturn("request");
        when(analysis.serializeInput("request")).thenReturn("input-json");
        when(analysis.execute("request")).thenReturn("response");
        when(analysis.runtimeStatus("response")).thenReturn("SUCCEEDED");
        when(analysis.renderOutput("response")).thenReturn("report");

        assertEquals(42L, useCase.submit(command));

        verify(incidents).openForAnomaly(org.mockito.ArgumentMatchers.eq(command), org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
        verify(analysis).execute("request");
        verify(incidents).linkInvestigation("incident-1", command, 42L);
        verify(catalog).markSucceeded(42L, "report");
    }

    @Test
    void ensureStorageDelegatesToCatalog() {
        ScheduledTaskExecutionCatalogPort catalog = mock(ScheduledTaskExecutionCatalogPort.class);
        ScheduledTaskExecutionUseCase<String, String> useCase = new ScheduledTaskExecutionUseCase<>(
                catalog,
                Runnable::run,
                mock(ScheduledTaskAnalysisPort.class));

        useCase.ensureStorage();

        verify(catalog).ensureStorage();
    }

    private ScheduledTaskExecutionCommand command(String payload) {
        return new ScheduledTaskExecutionCommand(
                7L,
                "支付巡检",
                "ops-agent",
                "SCHEDULED",
                payload);
    }
}
