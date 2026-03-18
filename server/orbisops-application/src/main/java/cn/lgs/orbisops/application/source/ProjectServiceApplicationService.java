package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.adapter.repository.IProjectServiceRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCapabilities;
import cn.lgs.orbisops.domain.source.service.ProjectServicePolicy;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class ProjectServiceApplicationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IProjectServiceRepository services;
    private final ISourceRepositoryRepository repositories;
    private final SourceProjectDirectoryPort projects;
    private final SourceExecutionResourcePort executionResources;
    private final SourceAuditPort audit;
    private final SourceTransactionPort transactions;
    private final ProjectServicePolicy policy;
    private final boolean enabled;

    public ProjectServiceApplicationService(
            IProjectServiceRepository services,
            ISourceRepositoryRepository repositories,
            SourceProjectDirectoryPort projects,
            SourceExecutionResourcePort executionResources,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            boolean enabled) {
        this(services, repositories, projects, executionResources, audit,
                transactions, enabled, new ProjectServicePolicy());
    }

    ProjectServiceApplicationService(
            IProjectServiceRepository services,
            ISourceRepositoryRepository repositories,
            SourceProjectDirectoryPort projects,
            SourceExecutionResourcePort executionResources,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            boolean enabled,
            ProjectServicePolicy policy) {
        if (services == null) throw new IllegalArgumentException("SOURCE_PROJECT_SERVICE_REPOSITORY_REQUIRED");
        if (repositories == null) throw new IllegalArgumentException("SOURCE_REPOSITORY_REPOSITORY_REQUIRED");
        if (projects == null) throw new IllegalArgumentException("SOURCE_PROJECT_DIRECTORY_REQUIRED");
        if (executionResources == null) throw new IllegalArgumentException("SOURCE_EXECUTION_RESOURCE_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("SOURCE_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("SOURCE_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("SOURCE_PROJECT_SERVICE_POLICY_REQUIRED");
        this.services = services;
        this.repositories = repositories;
        this.projects = projects;
        this.executionResources = executionResources;
        this.audit = audit;
        this.transactions = transactions;
        this.enabled = enabled;
        this.policy = policy;
    }

    public ProjectServiceCapabilities capabilities() {
        return new ProjectServiceCapabilities(
                enabled,
                Arrays.stream(BuildProfile.values()).map(Enum::name).toList(),
                false,
                enabled && services.available() ? services.listServices("").size() : 0);
    }

    public ProjectService upsert(ProjectServiceCandidate candidate, String actor) {
        assertEnabled();
        ProjectServiceCandidate normalized = policy.normalize(candidate);
        String normalizedActor = required(actor, "操作者不能为空");
        if (!projects.exists(normalized.projectId())) {
            throw new IllegalArgumentException("项目不存在：" + normalized.projectId());
        }
        if (repositories.find(normalized.projectId(), normalized.repositoryId()).isEmpty()) {
            throw new IllegalArgumentException("项目代码仓库不存在：" + normalized.repositoryId());
        }
        if (!normalized.deploymentResourceId().isBlank()
                && !executionResources.supportsService(
                normalized.projectId(), normalized.deploymentResourceId(), normalized.serviceId())) {
            throw new IllegalArgumentException(
                    "部署执行资源不存在、未启用或未配置该服务：" + normalized.deploymentResourceId());
        }
        ProjectService before = services.findService(normalized.projectId(), normalized.serviceId()).orElse(null);
        String now = now();
        ProjectService service = new ProjectService(
                normalized.serviceId(),
                normalized.projectId(),
                normalized.name(),
                normalized.repositoryId(),
                normalized.modulePath(),
                BuildProfile.require(normalized.buildProfile()),
                normalized.artifactPath(),
                normalized.deploymentResourceId(),
                normalized.healthUrl(),
                normalized.smokeUrls(),
                "READY",
                before == null ? now : before.createdAt(),
                now);
        return transactions.required(() -> {
            ProjectService saved = services.saveService(service);
            audit.record(new SourceAuditEvent(
                    saved.projectId(),
                    "project-service",
                    before == null ? "create" : "update",
                    saved.serviceId(),
                    before,
                    new ProjectServiceAuditResult(saved, normalizedActor)));
            return saved;
        });
    }

    public List<ProjectService> list(String projectId) {
        assertEnabled();
        return services.listServices(value(projectId));
    }

    public Optional<ProjectService> find(String projectId, String serviceId) {
        if (!enabled || value(projectId).isBlank() || value(serviceId).isBlank()) return Optional.empty();
        return services.findService(value(projectId), value(serviceId));
    }

    public ProjectServiceBuildCommand buildCommand(ProjectService service, Path repositoryRoot) {
        return policy.buildCommand(service, repositoryRoot);
    }

    private void assertEnabled() {
        if (!enabled) throw new IllegalStateException("代码仓库与服务目录能力未启用");
        if (!services.available() || !repositories.available()) {
            throw new IllegalStateException("代码仓库配置必须使用 MySQL 持久化");
        }
    }

    private String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String now() {
        return TIME.format(LocalDateTime.now());
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public record ProjectServiceAuditResult(ProjectService service, String actor) {
    }
}
