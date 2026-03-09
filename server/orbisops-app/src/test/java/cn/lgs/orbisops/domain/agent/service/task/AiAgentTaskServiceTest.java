package cn.lgs.orbisops.domain.agent.service.task;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAgentRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiAgentTaskScheduleVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAgentTaskServiceTest {

    @Test
    void delegatesScheduleQueriesToRepositoryWithoutFrameworkInjection() {
        IAgentRepository repository = mock(IAgentRepository.class);
        AiAgentTaskScheduleVO schedule = mock(AiAgentTaskScheduleVO.class);
        when(repository.queryAllValidTaskSchedule()).thenReturn(List.of(schedule));
        when(repository.queryAllInvalidTaskScheduleIds()).thenReturn(List.of(7L, 9L));
        AiAgentTaskService service = new AiAgentTaskService(repository);

        assertEquals(List.of(schedule), service.queryAllValidTaskSchedule());
        assertEquals(List.of(7L, 9L), service.queryAllInvalidTaskScheduleIds());
        verify(repository).queryAllValidTaskSchedule();
        verify(repository).queryAllInvalidTaskScheduleIds();
    }

    @Test
    void rejectsMissingRepository() {
        assertThrows(IllegalArgumentException.class, () -> new AiAgentTaskService(null));
    }
}
