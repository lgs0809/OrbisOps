package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.TaskScheduleAgentSnapshot;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.job.AgentTaskExecutionService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsTaskScheduleBoundaryAdaptersTest {

    @Test
    void agentSnapshotAdapterUsesProjectScopedPublishedQuery() {
        OpsAgentDefinitionQueryGateway gateway = mock(OpsAgentDefinitionQueryGateway.class);
        when(gateway.resolveForProject("ops-agent", 5, false, "payment"))
                .thenReturn(OpsAgentDefinition.builder().version(5).definitionHash("hash-v5").build());
        OpsTaskScheduleAgentSnapshotAdapter adapter = new OpsTaskScheduleAgentSnapshotAdapter(gateway);

        TaskScheduleAgentSnapshot snapshot = adapter.resolve("ops-agent", 5, "payment");

        assertEquals(new TaskScheduleAgentSnapshot(5, "hash-v5"), snapshot);
        verify(gateway).resolveForProject("ops-agent", 5, false, "payment");
    }

    @Test
    void cronAdapterOwnsSpringProtocolAndExplainsInvalidExpression() {
        OpsTaskScheduleCronValidationAdapter adapter = new OpsTaskScheduleCronValidationAdapter();

        assertDoesNotThrow(() -> adapter.validate("0 0/15 * * * ?"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.validate("not-a-cron"));

        assertEquals("Cron 表达式格式不正确，请使用 6 段格式，例如：0 0/15 * * * ?", error.getMessage());
    }

    @Test
    void executionAdapterProjectsTypedScheduleToExecutionCommand() {
        AgentTaskExecutionService executionService = mock(AgentTaskExecutionService.class);
        OpsTaskScheduleRuntimeConfigurationCodec codec = new OpsTaskScheduleRuntimeConfigurationCodec();
        OpsTaskScheduleExecutionAdapter adapter = new OpsTaskScheduleExecutionAdapter(executionService, codec);
        when(executionService.submitExecution(any(ScheduledTaskExecutionCommand.class))).thenReturn(42L);
        TaskScheduleDefinition schedule = definition();

        assertEquals(42L, adapter.submit(schedule, "MANUAL"));

        ArgumentCaptor<ScheduledTaskExecutionCommand> command =
                ArgumentCaptor.forClass(ScheduledTaskExecutionCommand.class);
        verify(executionService).submitExecution(command.capture());
        assertEquals(7L, command.getValue().scheduleId());
        assertEquals("MANUAL", command.getValue().triggerType());
        assertEquals("creator-a", command.getValue().createdBy());
        assertEquals(schedule.runtimeConfiguration(), codec.decode(command.getValue().runtimePayload()));
    }

    @Test
    void auditAdapterPreservesOperationNamesAndDeleteTombstone() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsTaskScheduleAuditAdapter adapter = new OpsTaskScheduleAuditAdapter(auditService);
        TaskScheduleDefinition schedule = definition();

        adapter.created(schedule);
        adapter.deleted(7L, schedule);

        verify(auditService).record("task-schedule", "create", "支付巡检", null, schedule);
        verify(auditService).record("task-schedule", "delete", "7", schedule, Map.of("deleted", true));
    }

    private TaskScheduleDefinition definition() {
        return new TaskScheduleDefinition(
                7L, "payment", "ops-agent", "支付巡检", "核心链路", "0 0/15 * * * ?",
                new TaskScheduleRuntimeConfiguration(
                        "payment", "LATEST_PUBLISHED", 5, "hash-v5", "检查错误率",
                        15, "5m", true, 3, 3, 120, 12, false, null, null),
                1,
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 6, 30), "creator-a");
    }
}
