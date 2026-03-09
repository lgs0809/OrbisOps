package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository.ITaskContextRepository;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;

import java.util.Optional;

public final class TaskContextQueryApplicationService {

    private final ITaskContextRepository repository;

    public TaskContextQueryApplicationService(ITaskContextRepository repository) {
        if (repository == null) throw new IllegalArgumentException("TASK_CONTEXT_REPOSITORY_REQUIRED");
        this.repository = repository;
    }

    public Optional<TaskContextSnapshot> find(String runId) {
        String normalized = required(runId);
        return repository.find(normalized);
    }

    public TaskContextSnapshot get(String runId) {
        String normalized = required(runId);
        return repository.find(normalized)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task Context 不存在：" + normalized));
    }

    private String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("runId 不能为空");
        return normalized;
    }
}
