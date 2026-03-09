package cn.lgs.orbisops.domain.agent.service.task;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAgentRepository;
import cn.lgs.orbisops.domain.agent.model.valobj.AiAgentTaskScheduleVO;
import cn.lgs.orbisops.domain.agent.service.ITaskService;

import java.util.List;

/** Pure Domain query service for scheduled Agent tasks. */
public final class AiAgentTaskService implements ITaskService {

    private final IAgentRepository repository;

    public AiAgentTaskService(IAgentRepository repository) {
        if (repository == null) throw new IllegalArgumentException("AGENT_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    @Override
    public List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule() {
        return repository.queryAllValidTaskSchedule();
    }

    @Override
    public List<Long> queryAllInvalidTaskScheduleIds() {
        return repository.queryAllInvalidTaskScheduleIds();
    }
}
