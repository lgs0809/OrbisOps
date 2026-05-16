package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCatalogPort;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.TaskExecutionCatalogPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskExecutionRepositoryTest {

    @Test
    void implementsBothTypedScheduleExecutionPorts() {
        TaskExecutionRepository repository = repositoryWithoutJdbc();

        assertInstanceOf(ScheduledTaskExecutionCatalogPort.class, repository);
        assertInstanceOf(TaskExecutionCatalogPort.class, repository);
    }

    @Test
    void degradesSafelyWhenJdbcIsUnavailable() {
        TaskExecutionRepository repository = repositoryWithoutJdbc();
        ScheduledTaskExecutionCommand command = new ScheduledTaskExecutionCommand(
                7L, "巡检", "ops-agent", "SCHEDULED", "{}");

        assertEquals(0L, repository.create(command));
        assertTrue(repository.list(7L, 20).isEmpty());
        repository.ensureStorage();
        repository.updateInput(42L, "input");
        repository.markSucceeded(42L, "output");
        repository.markFailed(42L, "error", null);
    }

    @Test
    void rejectsExecutionWithoutAgentBeforeDatabaseWrite() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JdbcTemplate.class));
        TaskExecutionRepository repository = new TaskExecutionRepository(provider);
        ScheduledTaskExecutionCommand command = new ScheduledTaskExecutionCommand(
                7L, "巡检", " ", "SCHEDULED", "{}");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> repository.create(command));

        assertEquals("执行 Agent 不能为空", error.getMessage());
    }

    private TaskExecutionRepository repositoryWithoutJdbc() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new TaskExecutionRepository(provider);
    }
}
