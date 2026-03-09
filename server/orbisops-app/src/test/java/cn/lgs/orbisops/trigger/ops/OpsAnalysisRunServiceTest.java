package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.alert.AlertEventApplicationService;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRun;
import cn.lgs.orbisops.application.analysis.AsyncAnalysisRunProcessManager;
import cn.lgs.orbisops.domain.alert.model.AlertRunOutcome;
import cn.lgs.orbisops.domain.analysis.model.AnalysisRunStatus;
import cn.lgs.orbisops.trigger.application.analysis.OpsAsyncAnalysisRunProtocolMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAnalysisRunServiceTest {

    @Test
    void facadeProjectsTypedRunAndDelegatesOperations() {
        @SuppressWarnings("unchecked")
        AsyncAnalysisRunProcessManager<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> manager =
                mock(AsyncAnalysisRunProcessManager.class);
        OpsAsyncAnalysisRunProtocolMapper mapper = new OpsAsyncAnalysisRunProtocolMapper();
        OpsAnalysisRunService service = new OpsAnalysisRunService(manager, mapper);
        OpsAgentRunRequestDTO request = request();
        OpsAnalysisResponseDTO response = response();
        AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> pending = new AsyncAnalysisRun<>(
                "run-1",
                AnalysisRunStatus.PENDING,
                request,
                null,
                null,
                LocalDateTime.of(2026, 7, 30, 8, 0),
                LocalDateTime.of(2026, 7, 30, 8, 0),
                null);
        AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> succeeded = pending.succeeded(
                response,
                1000L,
                LocalDateTime.of(2026, 7, 30, 8, 1));
        when(manager.submit(any(), any())).thenReturn(pending);
        when(manager.get("run-1")).thenReturn(Optional.of(succeeded));
        when(manager.list(10)).thenReturn(List.of(succeeded));
        when(manager.activeCountByProject("project-1")).thenReturn(3);
        when(manager.cancel("run-1")).thenReturn(true);

        OpsAgentRunRecordDTO submitted = service.submit(request, ignored -> response);

        assertEquals("run-1", submitted.getRunId());
        assertEquals("PENDING", submitted.getStatus());
        assertEquals("SUCCEEDED", service.get("run-1").orElseThrow().getStatus());
        assertEquals(List.of("run-1"), service.list(10).stream().map(OpsAgentRunRecordDTO::getRunId).toList());
        assertEquals(3, service.activeCountByProject("project-1"));
        assertTrue(service.cancel("run-1"));
    }

    @Test
    void executorAdapterRejectsWhenWorkersAndQueueAreSaturated() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1));
        OpsAsyncAnalysisExecutionAdapter adapter = new OpsAsyncAnalysisExecutionAdapter(
                executor,
                new OpsAnalysisRunSettings(10, true, true));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        adapter.submit("run-1", () -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        adapter.submit("run-2", () -> { });

        IllegalStateException error = assertThrows(IllegalStateException.class, adapter::assertCapacity);

        assertEquals("运维分析任务队列已满，请稍后重试。", error.getMessage());
        release.countDown();
        executor.shutdownNow();
    }

    @Test
    void alertOutcomeReporterKeepsTypedAlertProjection() {
        AlertEventApplicationService alertEvents = mock(AlertEventApplicationService.class);
        OpsAnalysisRunAlertOutcomeReporter reporter = new OpsAnalysisRunAlertOutcomeReporter(alertEvents);
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .triggerSource("ALERTMANAGER")
                .triggerEventId("fp-1")
                .build();

        reporter.report(request, "run-1", "SUCCEEDED", "done", "");

        ArgumentCaptor<AlertRunOutcome> outcome = ArgumentCaptor.forClass(AlertRunOutcome.class);
        verify(alertEvents).updateRunOutcome(outcome.capture());
        assertEquals("ALERTMANAGER", outcome.getValue().sourceType());
        assertEquals("fp-1", outcome.getValue().triggerEventId());
        assertEquals("run-1", outcome.getValue().runId());
        assertEquals("SUCCEEDED", outcome.getValue().runStatus());
    }

    @Test
    void protocolMapperRoundTripsExternalTimestampAndPayload() {
        OpsAsyncAnalysisRunProtocolMapper mapper = new OpsAsyncAnalysisRunProtocolMapper();
        AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> run = new AsyncAnalysisRun<>(
                "run-1",
                AnalysisRunStatus.SUCCEEDED,
                request(),
                response(),
                null,
                LocalDateTime.of(2026, 7, 30, 8, 0),
                LocalDateTime.of(2026, 7, 30, 8, 1),
                1000L);

        OpsAgentRunRecordDTO record = mapper.record(run);
        AsyncAnalysisRun<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> restored = mapper.run(record);

        assertEquals("2026-07-30 08:00:00", record.getCreatedAt());
        assertEquals(run.runId(), restored.runId());
        assertEquals(run.status(), restored.status());
        assertEquals(run.durationMs(), restored.durationMs());
    }

    private OpsAgentRunRequestDTO request() {
        return OpsAgentRunRequestDTO.builder()
                .projectId("project-1")
                .question("支付接口变慢并且出现 ERROR")
                .rangeMinutes(15)
                .promWindow("5m")
                .build();
    }

    private OpsAnalysisResponseDTO response() {
        return OpsAnalysisResponseDTO.builder()
                .analysisId("ops-analysis")
                .rangeMinutes(15)
                .promWindow("5m")
                .generatedAt("2026-07-30 08:00:00")
                .markdownReport("analysis complete")
                .build();
    }
}
