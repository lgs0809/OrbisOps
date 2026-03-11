package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistration;
import cn.lgs.orbisops.application.schedule.ScheduledTaskRegistryPort;
import cn.lgs.orbisops.application.schedule.TaskScheduleExecutionUseCase;
import cn.lgs.orbisops.trigger.scheduling.model.TaskScheduleVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskJobTest {

    @Test
    void projectsTypedRegistryEntriesAndTriggersExistingScheduleUseCaseById() {
        ScheduledTaskRegistryPort registry = mock(ScheduledTaskRegistryPort.class);
        TaskScheduleExecutionUseCase executionUseCase = mock(TaskScheduleExecutionUseCase.class);
        when(registry.listEnabled()).thenReturn(List.of(
                new ScheduledTaskRegistration(7L, "核心链路巡检", "0 0/15 * * * ?")));
        AgentTaskJob job = new AgentTaskJob(registry, executionUseCase);

        List<TaskScheduleVO> schedules = job.queryAllValidTaskSchedule();

        assertEquals(1, schedules.size());
        TaskScheduleVO schedule = schedules.get(0);
        assertEquals(7L, schedule.getId());
        assertEquals("核心链路巡检", schedule.getDescription());
        assertEquals("0 0/15 * * * ?", schedule.getCronExpression());
        assertNull(schedule.getTaskParam());

        schedule.getTaskExecutor().get().run();
        verify(executionUseCase).runScheduled(7L);
    }

    @Test
    void exposesInvalidScheduleIdsFromTypedRegistry() {
        ScheduledTaskRegistryPort registry = mock(ScheduledTaskRegistryPort.class);
        TaskScheduleExecutionUseCase executionUseCase = mock(TaskScheduleExecutionUseCase.class);
        when(registry.listInvalidIds()).thenReturn(List.of(7L, 9L));
        AgentTaskJob job = new AgentTaskJob(registry, executionUseCase);

        assertEquals(List.of(7L, 9L), job.queryAllInvalidTaskScheduleIds());
    }
}
