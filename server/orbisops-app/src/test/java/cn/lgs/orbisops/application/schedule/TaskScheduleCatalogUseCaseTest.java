package cn.lgs.orbisops.application.schedule;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskScheduleCatalogUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T06:30:00Z"),
            ZoneOffset.UTC);

    @Test
    void createNormalizesRuntimeSnapshotBeforePersistAndAudit() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleAuditPort audit = mock(TaskScheduleAuditPort.class);
        TaskScheduleAgentSnapshotPort agents = mock(TaskScheduleAgentSnapshotPort.class);
        TaskScheduleCronValidationPort cron = mock(TaskScheduleCronValidationPort.class);
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(catalog, audit, agents, cron, CLOCK);
        when(agents.resolve("ops-agent", null, "payment"))
                .thenReturn(new TaskScheduleAgentSnapshot(5, "ops-agent-v5"));
        when(catalog.insert(any())).thenReturn(true);

        assertTrue(useCase.create(command(
                null, " payment ", " ops-agent ", null, null,
                "支付巡检".repeat(80), "巡检".repeat(180),
                2000, "invalid", false, 30, 0, 999, 0,
                true, " channel-oncall ", " oncall-room "), "creator-a"));

        ArgumentCaptor<TaskScheduleDefinition> saved = ArgumentCaptor.forClass(TaskScheduleDefinition.class);
        InOrder order = inOrder(catalog, audit);
        order.verify(catalog).insert(saved.capture());
        order.verify(audit).created(saved.getValue());
        TaskScheduleDefinition schedule = saved.getValue();
        assertEquals("payment", schedule.projectId());
        assertEquals("creator-a", schedule.createdBy());
        assertEquals("ops-agent", schedule.agentId());
        assertEquals(128, schedule.taskName().length());
        assertEquals(255, schedule.description().length());
        assertEquals(LocalDateTime.of(2026, 7, 30, 6, 30), schedule.createTime());
        assertEquals(schedule.createTime(), schedule.updateTime());
        assertEquals("LATEST_PUBLISHED", schedule.runtimeConfiguration().agentBindingMode());
        assertEquals(5, schedule.runtimeConfiguration().agentVersion());
        assertEquals("ops-agent-v5", schedule.runtimeConfiguration().agentDefinitionHash());
        assertEquals(1440, schedule.runtimeConfiguration().rangeMinutes());
        assertEquals("5m", schedule.runtimeConfiguration().promWindow());
        assertFalse(schedule.runtimeConfiguration().includeRecentLogs());
        assertEquals(20, schedule.runtimeConfiguration().maxRounds());
        assertEquals(1, schedule.runtimeConfiguration().subAgentMaxIterations());
        assertEquals(300, schedule.runtimeConfiguration().nodeTimeoutSeconds());
        assertEquals(1, schedule.runtimeConfiguration().maxEvidenceItems());
        assertEquals("channel-oncall", schedule.runtimeConfiguration().notificationChannelId());
        verify(cron).validate("0 0/15 * * * ?");
        verify(agents).resolve("ops-agent", null, "payment");
    }

    @Test
    void pinnedBindingRequiresPositiveVersionBeforeAgentResolution() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleAuditPort audit = mock(TaskScheduleAuditPort.class);
        TaskScheduleAgentSnapshotPort agents = mock(TaskScheduleAgentSnapshotPort.class);
        TaskScheduleCronValidationPort cron = mock(TaskScheduleCronValidationPort.class);
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(catalog, audit, agents, cron, CLOCK);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.create(command(null, "payment", "ops-agent", "PINNED_VERSION", null,
                        "支付巡检", null, null, null, null, null, null, null, null,
                        null, null, null), "creator-a"));

        assertEquals("PINNED_VERSION 巡检任务必须选择 Agent 版本", error.getMessage());
        verify(agents, never()).resolve(any(), any(), any());
        verify(catalog, never()).insert(any());
    }

    @Test
    void updateRejectsCrossProjectBeforeCronAndAgentLookup() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleAuditPort audit = mock(TaskScheduleAuditPort.class);
        TaskScheduleAgentSnapshotPort agents = mock(TaskScheduleAgentSnapshotPort.class);
        TaskScheduleCronValidationPort cron = mock(TaskScheduleCronValidationPort.class);
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(catalog, audit, agents, cron, CLOCK);
        when(catalog.findById(7L)).thenReturn(definition(7L, "payment", 1));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.update(command(7L, "fulfillment", "ops-agent", null, null,
                        "支付巡检", null, null, null, null, null, null, null, null,
                        null, null, null)));

        assertEquals("任务 7 属于项目 payment，不能在项目 fulfillment 中操作", error.getMessage());
        verify(cron, never()).validate(any());
        verify(agents, never()).resolve(any(), any(), any());
        verify(catalog, never()).update(any());
    }

    @Test
    void failedUpdateDoesNotAudit() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleAuditPort audit = mock(TaskScheduleAuditPort.class);
        TaskScheduleAgentSnapshotPort agents = mock(TaskScheduleAgentSnapshotPort.class);
        TaskScheduleCronValidationPort cron = mock(TaskScheduleCronValidationPort.class);
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(catalog, audit, agents, cron, CLOCK);
        TaskScheduleDefinition before = definition(7L, "payment", 1);
        when(catalog.findById(7L)).thenReturn(before);
        when(agents.resolve("ops-agent", null, "payment"))
                .thenReturn(new TaskScheduleAgentSnapshot(6, "ops-agent-v6"));
        when(catalog.update(any())).thenReturn(false);

        assertFalse(useCase.update(command(7L, "payment", "ops-agent", null, null,
                "新巡检", "updated", null, null, null, null, null, null, null,
                null, null, null)));

        ArgumentCaptor<TaskScheduleDefinition> updated = ArgumentCaptor.forClass(TaskScheduleDefinition.class);
        verify(catalog).update(updated.capture());
        assertEquals(before.createTime(), updated.getValue().createTime());
        assertEquals(1, updated.getValue().status());
        assertEquals(6, updated.getValue().runtimeConfiguration().agentVersion());
        verify(audit, never()).updated(any(), any(), any());
    }

    @Test
    void statusChangeCopiesDefinitionAndAuditsOnlyAfterSuccessfulWrite() {
        TaskScheduleCatalogPort catalog = mock(TaskScheduleCatalogPort.class);
        TaskScheduleAuditPort audit = mock(TaskScheduleAuditPort.class);
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(
                catalog, audit, mock(TaskScheduleAgentSnapshotPort.class),
                mock(TaskScheduleCronValidationPort.class), CLOCK);
        TaskScheduleDefinition before = definition(7L, "payment", 1);
        when(catalog.findById(7L)).thenReturn(before);
        when(catalog.update(any())).thenReturn(true);

        assertTrue(useCase.updateStatus(7L, 0, "payment"));

        ArgumentCaptor<TaskScheduleDefinition> after = ArgumentCaptor.forClass(TaskScheduleDefinition.class);
        InOrder order = inOrder(catalog, audit);
        order.verify(catalog).findById(7L);
        order.verify(catalog).update(after.capture());
        order.verify(audit).statusChanged(7L, before, after.getValue());
        assertEquals(0, after.getValue().status());
        assertEquals(1, before.status());
        assertEquals(LocalDateTime.of(2026, 7, 30, 6, 30), after.getValue().updateTime());
    }

    @Test
    void listRequiresProjectScope() {
        TaskScheduleCatalogUseCase useCase = new TaskScheduleCatalogUseCase(
                mock(TaskScheduleCatalogPort.class),
                mock(TaskScheduleAuditPort.class),
                mock(TaskScheduleAgentSnapshotPort.class),
                mock(TaskScheduleCronValidationPort.class),
                CLOCK);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> useCase.listSchedules(" "));

        assertEquals("查询巡检任务必须提供 projectId", error.getMessage());
    }

    private TaskScheduleCommand command(
            Long id,
            String projectId,
            String agentId,
            String bindingMode,
            Integer agentVersion,
            String taskName,
            String description,
            Integer rangeMinutes,
            String promWindow,
            Boolean includeRecentLogs,
            Integer maxRounds,
            Integer subAgentMaxIterations,
            Integer nodeTimeoutSeconds,
            Integer maxEvidenceItems,
            Boolean notifyChannel,
            String notificationChannelId,
            String notificationTarget) {
        return new TaskScheduleCommand(
                id,
                projectId,
                agentId,
                bindingMode,
                agentVersion,
                taskName,
                description,
                "0 0/15 * * * ?",
                "检查错误率和延迟",
                null,
                rangeMinutes,
                promWindow,
                includeRecentLogs,
                maxRounds,
                subAgentMaxIterations,
                nodeTimeoutSeconds,
                maxEvidenceItems,
                notifyChannel,
                notificationChannelId,
                notificationTarget);
    }

    private TaskScheduleDefinition definition(Long id, String projectId, Integer status) {
        return new TaskScheduleDefinition(
                id,
                projectId,
                "ops-agent",
                "支付巡检",
                "核心链路",
                "0 0/15 * * * ?",
                new TaskScheduleRuntimeConfiguration(
                        projectId, "LATEST_PUBLISHED", 5, "ops-agent-v5", "检查错误率",
                        15, "5m", true, 3, 3, 120, 12, false, null, null),
                status,
                LocalDateTime.of(2026, 7, 29, 6, 0),
                LocalDateTime.of(2026, 7, 29, 6, 30));
    }
}
