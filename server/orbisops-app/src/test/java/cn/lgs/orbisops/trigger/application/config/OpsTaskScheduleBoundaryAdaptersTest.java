package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskExecutionView;
import cn.lgs.orbisops.application.schedule.TaskScheduleAgentSnapshot;
import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskExecutionRepository;
import cn.lgs.orbisops.domain.agent.model.entity.TaskExecutionRecord;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;
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
import static org.mockito.ArgumentMatchers.eq;
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
    void executionAdapterProjectsTypedScheduleToLegacyRuntimeRecord() {
        AgentTaskExecutionService executionService = mock(AgentTaskExecutionService.class);
        OpsTaskScheduleRuntimeConfigurationCodec codec = new OpsTaskScheduleRuntimeConfigurationCodec();
        OpsTaskScheduleExecutionAdapter adapter = new OpsTaskScheduleExecutionAdapter(executionService, codec);
        when(executionService.submitExecution(any(TaskScheduleRecord.class), eq("MANUAL"))).thenReturn(42L);
        TaskScheduleDefinition schedule = definition();

        assertEquals(42L, adapter.submit(schedule, "MANUAL"));

        ArgumentCaptor<TaskScheduleRecord> record = ArgumentCaptor.forClass(TaskScheduleRecord.class);
        verify(executionService).submitExecution(record.capture(), eq("MANUAL"));
        assertEquals(7L, record.getValue().getId());
        assertEquals(schedule.runtimeConfiguration(), codec.decode(record.getValue().getTaskParam()));
    }

    @Test
    void executionCatalogAdapterProjectsRepositoryHistory() {
        ITaskExecutionRepository repository = mock(ITaskExecutionRepository.class);
        OpsTaskExecutionCatalogAdapter adapter = new OpsTaskExecutionCatalogAdapter(repository);
        LocalDateTime started = LocalDateTime.of(2026, 7, 30, 6, 0);
        when(repository.list(7L, 20)).thenReturn(List.of(TaskExecutionRecord.builder()
                .id(42L)
                .scheduleId(7L)
                .taskName("支付巡检")
                .agentId("ops-agent")
                .triggerType("MANUAL")
                .status("SUCCESS")
                .startedAt(started)
                .input("in")
                .output("out")
                .build()));

        List<TaskExecutionView> result = adapter.list(7L, 20);

        assertEquals(1, result.size());
        assertEquals(42L, result.get(0).id());
        assertEquals(started, result.get(0).startedAt());
        assertEquals("SUCCESS", result.get(0).status());
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
                LocalDateTime.of(2026, 7, 30, 6, 30));
    }
}
