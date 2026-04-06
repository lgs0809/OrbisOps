package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceDraft;
import cn.lgs.orbisops.domain.execution.model.ExecutionSourceResource;
import cn.lgs.orbisops.domain.execution.service.ExecutionResourcePolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class ExecutionResourceCommandApplicationService {

    private final IExecutionResourceRepository repository;
    private final ExecutionResourceRuntimeDirectoryApplicationService directory;
    private final ExecutionResourceProjectPort projects;
    private final ExecutionAuditPort audit;
    private final ExecutionResourcePolicy policy;
    private final boolean enabled;
    private final Supplier<LocalDateTime> clock;

    public ExecutionResourceCommandApplicationService(
            IExecutionResourceRepository repository,
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            ExecutionResourceProjectPort projects,
            ExecutionAuditPort audit,
            boolean enabled) {
        this(repository, directory, projects, audit, new ExecutionResourcePolicy(), enabled,
                LocalDateTime::now);
    }

    ExecutionResourceCommandApplicationService(
            IExecutionResourceRepository repository,
            ExecutionResourceRuntimeDirectoryApplicationService directory,
            ExecutionResourceProjectPort projects,
            ExecutionAuditPort audit,
            ExecutionResourcePolicy policy,
            boolean enabled,
            Supplier<LocalDateTime> clock) {
        if (repository == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_REPOSITORY_REQUIRED");
        if (directory == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_DIRECTORY_REQUIRED");
        if (projects == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_PROJECTS_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("EXECUTION_AUDIT_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_POLICY_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_CLOCK_REQUIRED");
        this.repository = repository;
        this.directory = directory;
        this.projects = projects;
        this.audit = audit;
        this.policy = policy;
        this.enabled = enabled;
        this.clock = clock;
    }

    public synchronized ExecutionResource upsert(ExecutionResourceDraft draft, String actor) {
        assertEnabled();
        if (draft == null) throw new IllegalArgumentException("执行资源配置不能为空");
        String normalizedActor = required(actor, "EXECUTION_ACTOR_REQUIRED");
        String projectId = text(draft.projectId());
        if (!projects.exists(projectId)) {
            throw new IllegalArgumentException("项目不存在：" + projectId);
        }
        ExecutionAdapterType adapter = ExecutionAdapterType.require(draft.adapter());
        ExecutionSourceResource sourceResource = resolveSourceResource(draft, adapter).orElse(null);
        List<String> projectEnvironments = projects.environments(projectId);
        ExecutionResourceDraft normalized = policy.normalize(
                draft, projectEnvironments, sourceResource);
        policy.assertWorkerResourceUnique(
                directory.existsWorkerResourceOutsideProject(
                        normalized.workerId(), normalized.resourceId(), normalized.projectId()),
                normalized.resourceId());
        ExecutionResource before = directory.find(
                normalized.projectId(), normalized.resourceId()).orElse(null);
        ExecutionResource candidate = policy.materialize(normalized, before, clock.get());
        ExecutionResource saved = repository.save(candidate);
        directory.publish(saved);
        audit.record(saved.projectId(), "execution-resource",
                before == null ? "create" : "update", saved.resourceId(), before,
                new AuditResult(saved, normalizedActor));
        return saved;
    }

    public synchronized ExecutionResource updateStatus(
            String projectId,
            String resourceId,
            String status,
            String actor) {
        assertEnabled();
        String normalizedActor = required(actor, "EXECUTION_ACTOR_REQUIRED");
        String project = required(projectId, "EXECUTION_PROJECT_ID_REQUIRED");
        String resource = required(resourceId, "EXECUTION_RESOURCE_ID_REQUIRED");
        ExecutionResource before = directory.find(project, resource)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EXECUTION_RESOURCE_NOT_FOUND:" + resource));
        ExecutionResource candidate = before.withStatus(policy.status(status), clock.get());
        ExecutionResource saved = repository.save(candidate);
        directory.publish(saved);
        audit.record(project, "execution-resource", "status", resource, before,
                new AuditResult(saved, normalizedActor));
        return saved;
    }

    private Optional<ExecutionSourceResource> resolveSourceResource(
            ExecutionResourceDraft draft,
            ExecutionAdapterType adapter) {
        if (!policy.requiresSourceResource(adapter)) return Optional.empty();
        String sourceResourceId = policy.sourceResourceId(draft);
        return projects.findSourceResource(text(draft.projectId()), sourceResourceId);
    }

    private void assertEnabled() {
        if (!enabled) throw new IllegalStateException("项目执行资源能力未启用");
    }

    private String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    public record AuditResult(ExecutionResource resource, String actor) {
    }
}
