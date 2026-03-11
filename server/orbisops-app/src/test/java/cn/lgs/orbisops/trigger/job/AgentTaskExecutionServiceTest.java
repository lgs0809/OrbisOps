package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionUseCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskExecutionServiceTest {

    @Test
    void typedCommandIsSubmittedWithoutLegacyScheduleProjection() {
        @SuppressWarnings("unchecked")
        ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> useCase =
                mock(ScheduledTaskExecutionUseCase.class);
        AgentTaskExecutionService service = new AgentTaskExecutionService(useCase);
        when(useCase.submit(any())).thenReturn(42L);
        ScheduledTaskExecutionCommand requested = new ScheduledTaskExecutionCommand(
                7L,
                "支付链路巡检",
                "payment-ops-agent",
                "SCHEDULED",
                "{\"projectId\":\"payment\"}");

        assertEquals(42L, service.submitExecution(requested));

        verify(useCase).submit(requested);
    }

    @Test
    void initializationDelegatesStorageBootstrap() {
        @SuppressWarnings("unchecked")
        ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> useCase =
                mock(ScheduledTaskExecutionUseCase.class);
        AgentTaskExecutionService service = new AgentTaskExecutionService(useCase);

        service.ensureExecutionTable();

        verify(useCase).ensureStorage();
    }
}
