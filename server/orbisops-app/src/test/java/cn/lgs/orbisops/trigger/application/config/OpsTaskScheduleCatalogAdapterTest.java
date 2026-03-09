package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.domain.agent.adapter.repository.ITaskScheduleRepository;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsTaskScheduleCatalogAdapterTest {

    @Test
    void insertMapsTypedDefinitionToRepositoryRecordAndJsonProtocol() {
        ITaskScheduleRepository repository = mock(ITaskScheduleRepository.class);
        OpsTaskScheduleRuntimeConfigurationCodec codec = new OpsTaskScheduleRuntimeConfigurationCodec();
        OpsTaskScheduleCatalogAdapter adapter = new OpsTaskScheduleCatalogAdapter(repository, codec);
        when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        TaskScheduleDefinition definition = definition(7L, "payment");

        assertTrue(adapter.insert(definition));

        ArgumentCaptor<TaskScheduleRecord> record = ArgumentCaptor.forClass(TaskScheduleRecord.class);
        verify(repository).insert(record.capture());
        assertEquals("payment", record.getValue().getProjectId());
        assertEquals("ops-agent", record.getValue().getAgentId());
        assertEquals(definition.runtimeConfiguration(), codec.decode(record.getValue().getTaskParam()));
    }

    @Test
    void findMapsRepositoryRecordToTypedDefinition() {
        ITaskScheduleRepository repository = mock(ITaskScheduleRepository.class);
        OpsTaskScheduleRuntimeConfigurationCodec codec = new OpsTaskScheduleRuntimeConfigurationCodec();
        OpsTaskScheduleCatalogAdapter adapter = new OpsTaskScheduleCatalogAdapter(repository, codec);
        TaskScheduleDefinition expected = definition(7L, "payment");
        when(repository.queryById(7L)).thenReturn(TaskScheduleRecord.builder()
                .id(7L)
                .projectId("payment")
                .agentId("ops-agent")
                .taskName("支付巡检")
                .description("核心链路")
                .cronExpression("0 0/15 * * * ?")
                .taskParam(codec.encode(expected.runtimeConfiguration()))
                .status(1)
                .createTime(expected.createTime())
                .updateTime(expected.updateTime())
                .build());

        TaskScheduleDefinition actual = adapter.findById(7L);

        assertEquals(expected, actual);
    }

    @Test
    void listRejectsLegacyTextConfigurationFailClosed() {
        ITaskScheduleRepository repository = mock(ITaskScheduleRepository.class);
        OpsTaskScheduleCatalogAdapter adapter = new OpsTaskScheduleCatalogAdapter(
                repository, new OpsTaskScheduleRuntimeConfigurationCodec());
        when(repository.queryByProjectId("payment")).thenReturn(List.of(TaskScheduleRecord.builder()
                .id(7L)
                .projectId("payment")
                .taskParam("旧版文本 prompt")
                .build()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.findByProjectId("payment"));

        assertEquals("周期任务仍使用旧版文本配置，请重新创建任务", error.getMessage());
    }

    private TaskScheduleDefinition definition(Long id, String projectId) {
        return new TaskScheduleDefinition(
                id, projectId, "ops-agent", "支付巡检", "核心链路", "0 0/15 * * * ?",
                new TaskScheduleRuntimeConfiguration(
                        projectId, "LATEST_PUBLISHED", 5, "hash-v5", "检查错误率",
                        15, "5m", true, 3, 3, 120, 12, false, null, null),
                1,
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 6, 30));
    }
}
