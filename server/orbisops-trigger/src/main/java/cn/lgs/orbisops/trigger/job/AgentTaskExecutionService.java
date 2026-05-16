package cn.lgs.orbisops.trigger.job;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionUseCase;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

/** Compatibility facade for submitting scheduled Agent task executions. */
@Service
public class AgentTaskExecutionService {

    private final ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> executionUseCase;

    public AgentTaskExecutionService(
            ScheduledTaskExecutionUseCase<OpsAgentRunRequestDTO, OpsAnalysisResponseDTO> executionUseCase) {
        if (executionUseCase == null) {
            throw new IllegalArgumentException("SCHEDULED_TASK_EXECUTION_USE_CASE_REQUIRED");
        }
        this.executionUseCase = executionUseCase;
    }

    @PostConstruct
    public void ensureExecutionTable() {
        executionUseCase.ensureStorage();
    }

    public Long submitExecution(ScheduledTaskExecutionCommand command) {
        return executionUseCase.submit(command);
    }

}
