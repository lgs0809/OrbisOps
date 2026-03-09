package cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository;

import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;

import java.util.Optional;

public interface ITaskContextRepository {

    Optional<TaskContextSnapshot> find(String runId);

    Optional<TaskContextSnapshot> trySave(TaskContextSnapshot snapshot, int expectedVersion);
}
