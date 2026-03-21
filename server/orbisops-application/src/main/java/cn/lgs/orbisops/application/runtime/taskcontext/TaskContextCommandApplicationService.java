package cn.lgs.orbisops.application.runtime.taskcontext;

import cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository.ITaskContextRepository;
import cn.lgs.orbisops.domain.runtime.taskcontext.exception.TaskContextVersionConflictException;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.domain.runtime.taskcontext.service.TaskContextPolicy;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public final class TaskContextCommandApplicationService {

    private final ITaskContextRepository repository;
    private final TaskContextAuditPort audit;
    private final TaskContextPolicy policy;
    private final Supplier<LocalDateTime> clock;

    public TaskContextCommandApplicationService(
            ITaskContextRepository repository,
            TaskContextAuditPort audit) {
        this(repository, audit, new TaskContextPolicy(), LocalDateTime::now);
    }

    TaskContextCommandApplicationService(
            ITaskContextRepository repository,
            TaskContextAuditPort audit,
            TaskContextPolicy policy,
            Supplier<LocalDateTime> clock) {
        if (repository == null) throw new IllegalArgumentException("TASK_CONTEXT_REPOSITORY_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("TASK_CONTEXT_AUDIT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("TASK_CONTEXT_POLICY_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("TASK_CONTEXT_CLOCK_REQUIRED");
        this.repository = repository;
        this.audit = audit;
        this.policy = policy;
        this.clock = clock;
    }

    public Optional<TaskContextSnapshot> start(TaskContextStartCommand command) {
        if (command == null || text(command.runId()).isBlank()) return Optional.empty();
        return mutate(command.runId(), existing -> policy.start(
                command.runId(), command.sessionId(), command.projectId(), command.agentId(),
                command.goal(), command.usedSkills(), existing, clock.get()));
    }

    public Optional<TaskContextSnapshot> progress(TaskContextProgressCommand command) {
        if (command == null || text(command.runId()).isBlank()) return Optional.empty();
        return mutate(command.runId(), existing -> policy.progress(
                command.runId(), command.sessionId(), command.projectId(), command.agentId(),
                command.goal(), command.usedSkills(), command.taskState(), command.events(),
                command.summary(), existing, clock.get()));
    }

    public Optional<TaskContextSnapshot> finish(TaskContextFinishCommand command) {
        if (command == null || text(command.runId()).isBlank()) return Optional.empty();
        return mutate(command.runId(), existing -> policy.finish(
                command.runId(), command.sessionId(), command.projectId(), command.agentId(),
                command.goal(), command.usedSkills(), command.status(), command.output(),
                command.events(), existing, clock.get()));
    }

    private Optional<TaskContextSnapshot> mutate(
            String runId,
            Function<TaskContextSnapshot, TaskContextSnapshot> mutation) {
        for (int attempt = 0; attempt < 2; attempt++) {
            TaskContextSnapshot before;
            try {
                before = repository.find(runId).orElse(null);
            } catch (RuntimeException unavailable) {
                return Optional.empty();
            }
            int expectedVersion = before == null ? 0 : before.version();
            TaskContextSnapshot candidate = mutation.apply(before);
            try {
                Optional<TaskContextSnapshot> saved = repository.trySave(candidate, expectedVersion);
                saved.ifPresent(after -> recordAudit(before, after));
                return saved;
            } catch (TaskContextVersionConflictException conflict) {
                if (attempt == 1) return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void recordAudit(TaskContextSnapshot before, TaskContextSnapshot after) {
        try {
            audit.record(before, after);
        } catch (RuntimeException ignored) {
            // Task Context is operational memory; audit failure must not break the Work Session.
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
