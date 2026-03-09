package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.TaskExecutionRecord;

import java.util.List;

public interface ITaskExecutionRepository {

    void ensureTable();

    Long create(Long scheduleId, String taskName, String agentId, String triggerType);

    void updateInput(Long executionId, String input);

    void markSuccess(Long executionId, String output);

    void markFailed(Long executionId, String errorMessage, String output);

    List<TaskExecutionRecord> list(Long scheduleId, int limit);
}
