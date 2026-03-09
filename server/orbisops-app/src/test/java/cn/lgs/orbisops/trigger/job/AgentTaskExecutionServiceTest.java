package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionUseCase;
import cn.lgs.orbisops.domain.agent.model.entity.TaskScheduleRecord;
import cn.lgs.orbisops.domain.agent.model.valobj.AiAgentTaskScheduleVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskExecutionServiceTest {

    @Test
    void recordScheduleProjectsOpaquePayloadToTypedCommand() {
        @SuppressWarnings("unchecked")
        ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> useCase =
                mock(ScheduledTaskExecutionUseCase.class);
        AgentTaskExecutionService service = new AgentTaskExecutionService(useCase);
        when(useCase.submit(any())).thenReturn(42L);
        TaskScheduleRecord schedule = TaskScheduleRecord.builder()
                .id(7L)
                .agentId("payment-ops-agent")
                .taskName("支付链路巡检")
                .taskParam("{\"projectId\":\"payment\"}")
                .build();

        assertEquals(42L, service.submitExecution(schedule, "SCHEDULED"));

        ArgumentCaptor<ScheduledTaskExecutionCommand> command =
                ArgumentCaptor.forClass(ScheduledTaskExecutionCommand.class);
        verify(useCase).submit(command.capture());
        assertEquals(7L, command.getValue().scheduleId());
        assertEquals("支付链路巡检", command.getValue().taskName());
        assertEquals("payment-ops-agent", command.getValue().agentId());
        assertEquals("SCHEDULED", command.getValue().triggerType());
        assertEquals("{\"projectId\":\"payment\"}", command.getValue().runtimePayload());
    }

    @Test
    void legacyValueObjectProjectsDescriptionAndPayload() {
        @SuppressWarnings("unchecked")
        ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> useCase =
                mock(ScheduledTaskExecutionUseCase.class);
        AgentTaskExecutionService service = new AgentTaskExecutionService(useCase);
        when(useCase.submit(any())).thenReturn(43L);
        AiAgentTaskScheduleVO schedule = new AiAgentTaskScheduleVO();
        schedule.setId(8L);
        schedule.setAgentId("ops-agent");
        schedule.setDescription("旧调度入口");
        schedule.setTaskParam("旧版文本 prompt");

        assertEquals(43L, service.submitExecution(schedule, "MANUAL"));

        ArgumentCaptor<ScheduledTaskExecutionCommand> command =
                ArgumentCaptor.forClass(ScheduledTaskExecutionCommand.class);
        verify(useCase).submit(command.capture());
        assertEquals("旧调度入口", command.getValue().taskName());
        assertEquals("旧版文本 prompt", command.getValue().runtimePayload());
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
