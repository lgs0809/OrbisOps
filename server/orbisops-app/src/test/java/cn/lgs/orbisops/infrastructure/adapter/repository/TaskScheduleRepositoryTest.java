package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.TaskScheduleDefinition;
import cn.lgs.orbisops.application.schedule.TaskScheduleRuntimeConfiguration;
import cn.lgs.orbisops.application.schedule.TaskScheduleScreeningConfiguration;
import cn.lgs.orbisops.infrastructure.dao.IAiAgentTaskScheduleDao;
import cn.lgs.orbisops.infrastructure.dao.po.AiAgentTaskSchedule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskScheduleRepositoryTest {

    @Test
    void persistsTypedScheduleAndRuntimePayloadWithoutLegacyDomainRecord() {
        IAiAgentTaskScheduleDao dao = mock(IAiAgentTaskScheduleDao.class);
        TaskScheduleRepository repository = repository(dao);
        TaskScheduleDefinition definition = definition("payment");
        when(dao.insert(any(AiAgentTaskSchedule.class))).thenReturn(1);

        assertTrue(repository.insert(definition));

        ArgumentCaptor<AiAgentTaskSchedule> row = ArgumentCaptor.forClass(AiAgentTaskSchedule.class);
        verify(dao).insert(row.capture());
        assertEquals("payment", row.getValue().getProjectId());
        assertEquals("ops-agent", row.getValue().getAgentId());
        TaskScheduleRuntimeConfiguration decoded = new TaskSchedulePersistenceCodec(new ObjectMapper())
                .decode(row.getValue().getTaskParam());
        assertEquals(definition.runtimeConfiguration(), decoded);
    }

    @Test
    void restoresTypedDefinitionAndFallsBackToRuntimeProjectIdForLegacyRow() {
        IAiAgentTaskScheduleDao dao = mock(IAiAgentTaskScheduleDao.class);
        TaskScheduleRepository repository = repository(dao);
        TaskScheduleDefinition expected = definition("payment");
        String payload = new TaskSchedulePersistenceCodec(new ObjectMapper()).encode(expected.runtimeConfiguration());
        when(dao.queryById(7L)).thenReturn(AiAgentTaskSchedule.builder()
                .id(7L)
                .projectId(" ")
                .agentId("ops-agent")
                .taskName("支付巡检")
                .description("核心链路")
                .cronExpression("0 0/15 * * * ?")
                .taskParam(payload)
                .status(1)
                .createTime(expected.createTime())
                .updateTime(expected.updateTime())
                .build());

        TaskScheduleDefinition actual = repository.findById(7L);

        assertEquals(expected, actual);
    }

    @Test
    void rejectsLegacyTextPersistencePayloadFailClosed() {
        IAiAgentTaskScheduleDao dao = mock(IAiAgentTaskScheduleDao.class);
        TaskScheduleRepository repository = repository(dao);
        when(dao.queryByProjectId("payment")).thenReturn(List.of(AiAgentTaskSchedule.builder()
                .id(7L)
                .projectId("payment")
                .taskParam("旧版文本 prompt")
                .build()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> repository.findByProjectId("payment"));

        assertEquals("周期任务仍使用旧版文本配置，请重新创建任务", error.getMessage());
    }

    @Test
    void projectsMinimalSchedulerRegistryWithoutDecodingRuntimePayload() {
        IAiAgentTaskScheduleDao dao = mock(IAiAgentTaskScheduleDao.class);
        TaskScheduleRepository repository = repository(dao);
        when(dao.queryAllValidTaskSchedule()).thenReturn(List.of(AiAgentTaskSchedule.builder()
                .id(7L)
                .description("核心链路巡检")
                .cronExpression("0 0/15 * * * ?")
                .taskParam("旧版文本 prompt")
                .build()));
        when(dao.queryAllInvalidTaskScheduleIds()).thenReturn(List.of(9L));

        assertEquals(List.of(
                new cn.lgs.orbisops.application.schedule.ScheduledTaskRegistration(
                        7L, "核心链路巡检", "0 0/15 * * * ?")), repository.listEnabled());
        assertEquals(List.of(9L), repository.listInvalidIds());
    }

    @Test
    void degradesToEmptyCatalogWhenScheduleDaoIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiAgentTaskScheduleDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        TaskScheduleRepository repository = new TaskScheduleRepository(provider, new ObjectMapper());

        assertEquals(null, repository.findById(7L));
        assertTrue(repository.findByProjectId("payment").isEmpty());
        assertEquals(false, repository.insert(definition("payment")));
        assertEquals(false, repository.update(definition("payment")));
        assertEquals(false, repository.deleteById(7L));
    }

    private TaskScheduleRepository repository(IAiAgentTaskScheduleDao dao) {
        @SuppressWarnings("unchecked")
        ObjectProvider<IAiAgentTaskScheduleDao> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dao);
        return new TaskScheduleRepository(provider, new ObjectMapper());
    }

    private TaskScheduleDefinition definition(String projectId) {
        return new TaskScheduleDefinition(
                7L,
                projectId,
                "ops-agent",
                "支付巡检",
                "核心链路",
                "0 0/15 * * * ?",
                new TaskScheduleRuntimeConfiguration(
                        projectId,
                        "LATEST_PUBLISHED",
                        5,
                        "hash-v5",
                        "检查错误率",
                        15,
                        "5m",
                        true,
                        3,
                        3,
                        120,
                        12,
                        false,
                        null,
                        null,
                        new TaskScheduleScreeningConfiguration(
                                true, "PROMETHEUS", "/metrics", 5D, 80D, 85D, 0.9D)),
                1,
                LocalDateTime.of(2026, 7, 30, 6, 0),
                LocalDateTime.of(2026, 7, 30, 6, 30));
    }
}
