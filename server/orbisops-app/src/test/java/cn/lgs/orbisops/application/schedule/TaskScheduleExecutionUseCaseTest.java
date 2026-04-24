package cn.lgs.orbisops.application.schedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskScheduleExecutionUseCaseTest {

    @Test
    void runNowReadsScopedScheduleBeforeSubmission() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleExecutionPort execution = mock(TaskScheduleExecutionPort.class);
        TaskExecutionCatalogPort history = mock(TaskExecutionCatalogPort.class);
        TaskScheduleExecutionUseCase useCase = new TaskScheduleExecutionUseCase(catalog, execution, history);
        TaskScheduleDefinition schedule = definition(7L, "payment");
        when(catalog.findById(7L)).thenReturn(schedule);
        when(execution.submit(schedule, "MANUAL")).thenReturn(42L);

        assertEquals(42L, useCase.runNow(7L, " payment "));

        verify(catalog).findById(7L);
        verify(execution).submit(schedule, "MANUAL");
    }

    @Test
    void runScheduledUsesAuthoritativeCatalogAndScheduledTriggerType() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleExecutionPort execution = mock(TaskScheduleExecutionPort.class);
        TaskExecutionCatalogPort history = mock(TaskExecutionCatalogPort.class);
        TaskScheduleExecutionUseCase useCase = new TaskScheduleExecutionUseCase(catalog, execution, history);
        TaskScheduleDefinition schedule = definition(7L, "payment");
        when(catalog.findById(7L)).thenReturn(schedule);
        when(execution.submit(schedule, "SCHEDULED")).thenReturn(43L);

        assertEquals(43L, useCase.runScheduled(7L));

        verify(catalog).findById(7L);
        verify(execution).submit(schedule, "SCHEDULED");
    }

    @Test
    void runNowRejectsCrossProjectWithoutSubmission() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleExecutionPort execution = mock(TaskScheduleExecutionPort.class);
        TaskExecutionCatalogPort history = mock(TaskExecutionCatalogPort.class);
        TaskScheduleExecutionUseCase useCase = new TaskScheduleExecutionUseCase(catalog, execution, history);
        when(catalog.findById(7L)).thenReturn(definition(7L, "payment"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.runNow(7L, "fulfillment"));

        assertEquals("任务 7 属于项目 payment，不能在项目 fulfillment 中操作", error.getMessage());
        verify(execution, never()).submit(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void executionHistoryClampsLimitAndReturnsImmutableCopy() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleExecutionPort execution = mock(TaskScheduleExecutionPort.class);
        TaskExecutionCatalogPort history = mock(TaskExecutionCatalogPort.class);
        TaskScheduleExecutionUseCase useCase = new TaskScheduleExecutionUseCase(catalog, execution, history);
        when(catalog.findById(7L)).thenReturn(definition(7L, "payment"));
        TaskExecutionView value = new TaskExecutionView(
                42L, 7L, "支付巡检", "ops-agent", "MANUAL", "SUCCESS",
                LocalDateTime.now(), LocalDateTime.now(), "in", "out", null);
        when(history.list(7L, 100)).thenReturn(List.of(value));

        List<TaskExecutionView> result = useCase.listExecutions("payment", 7L, 999);

        assertEquals(List.of(value), result);
        verify(history).list(7L, 100);
        assertThrows(UnsupportedOperationException.class, () -> result.add(value));
    }

    @Test
    void executionHistoryRequiresScheduleId() {
        TaskScheduleExecutionUseCase useCase = new TaskScheduleExecutionUseCase(
                mock(TaskScheduleCatalogPort.class),
                mock(TaskScheduleExecutionPort.class),
                mock(TaskExecutionCatalogPort.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.listExecutions("payment", null, 20));

        assertEquals("查询巡检记录必须提供 scheduleId", error.getMessage());
    }

    private TaskScheduleDefinition definition(Long id, String projectId) {
        return new TaskScheduleDefinition(
                id,
                projectId,
                "ops-agent",
                "支付巡检",
                null,
                "0 0/15 * * * ?",
                new TaskScheduleRuntimeConfiguration(
                        projectId, "LATEST_PUBLISHED", 5, "hash-v5", "检查错误率",
                        15, "5m", true, 3, 3, 120, 12, false, null, null),
                1,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
