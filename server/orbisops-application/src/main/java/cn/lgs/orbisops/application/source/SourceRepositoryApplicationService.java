package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.adapter.repository.IDeploymentRevisionRepository;
import cn.lgs.orbisops.domain.source.adapter.repository.ISourceRepositoryRepository;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.DeploymentRevisionCandidate;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCapabilities;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import cn.lgs.orbisops.domain.source.service.SourceRepositoryPolicy;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public final class SourceRepositoryApplicationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> OPERATIONS = List.of(
            "REGISTER_REPOSITORY",
            "MAP_DEPLOYED_COMMIT",
            "READ_FILE_AT_COMMIT",
            "SEARCH_CODE_AT_COMMIT");

    private final ISourceRepositoryRepository repositories;
    private final IDeploymentRevisionRepository deployments;
    private final SourceGitPort git;
    private final SourceProjectDirectoryPort projects;
    private final SourceMcpProjectionPort mcpProjection;
    private final SourceMcpRuntimePort mcpRuntime;
    private final SourceAuditPort audit;
    private final SourceTransactionPort transactions;
    private final SourceRepositoryPolicy policy;
    private final boolean enabled;

    public SourceRepositoryApplicationService(
            ISourceRepositoryRepository repositories,
            IDeploymentRevisionRepository deployments,
            SourceGitPort git,
            SourceProjectDirectoryPort projects,
            SourceMcpProjectionPort mcpProjection,
            SourceMcpRuntimePort mcpRuntime,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            boolean enabled) {
        this(repositories, deployments, git, projects, mcpProjection, mcpRuntime,
                audit, transactions, enabled, new SourceRepositoryPolicy());
    }

    SourceRepositoryApplicationService(
            ISourceRepositoryRepository repositories,
            IDeploymentRevisionRepository deployments,
            SourceGitPort git,
            SourceProjectDirectoryPort projects,
            SourceMcpProjectionPort mcpProjection,
            SourceMcpRuntimePort mcpRuntime,
            SourceAuditPort audit,
            SourceTransactionPort transactions,
            boolean enabled,
            SourceRepositoryPolicy policy) {
        if (repositories == null) throw new IllegalArgumentException("SOURCE_REPOSITORY_REPOSITORY_REQUIRED");
        if (deployments == null) throw new IllegalArgumentException("SOURCE_DEPLOYMENT_REPOSITORY_REQUIRED");
        if (git == null) throw new IllegalArgumentException("SOURCE_GIT_PORT_REQUIRED");
        if (projects == null) throw new IllegalArgumentException("SOURCE_PROJECT_DIRECTORY_REQUIRED");
        if (mcpProjection == null) throw new IllegalArgumentException("SOURCE_MCP_PROJECTION_REQUIRED");
        if (mcpRuntime == null) throw new IllegalArgumentException("SOURCE_MCP_RUNTIME_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("SOURCE_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("SOURCE_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("SOURCE_REPOSITORY_POLICY_REQUIRED");
        this.repositories = repositories;
        this.deployments = deployments;
        this.git = git;
        this.projects = projects;
        this.mcpProjection = mcpProjection;
        this.mcpRuntime = mcpRuntime;
        this.audit = audit;
        this.transactions = transactions;
        this.enabled = enabled;
        this.policy = policy;
    }

    public SourceRepositoryCapabilities capabilities() {
        return new SourceRepositoryCapabilities(
                enabled, "LOCAL_OR_MCP_GIT", OPERATIONS, false, git.allowedRootsConfigured());
    }

    public SourceRepository register(SourceRepositoryCandidate candidate, String actor) {
        assertEnabled();
        SourceRepositoryCandidate normalized = policy.repository(candidate);
        String normalizedActor = policy.actor(actor);
        if (!projects.exists(normalized.projectId())) {
            throw new IllegalArgumentException("项目不存在：" + normalized.projectId());
        }
        SourceRepository existingById = repositories.findByRepositoryId(normalized.repositoryId()).orElse(null);
        if (existingById != null && !normalized.projectId().equals(existingById.projectId())) {
            throw new IllegalArgumentException("repositoryId 已被其他项目使用：" + normalized.repositoryId());
        }
        String commitSha = resolveCommit(normalized);
        String now = now();
        SourceRepository repository = new SourceRepository(
                normalized.repositoryId(),
                policy.mcpId(normalized.repositoryId()),
                normalized.projectId(),
                normalized.name(),
                normalized.localPath(),
                normalized.accessMode(),
                normalized.codeMcpId(),
                normalized.logicalRoot(),
                normalized.defaultRevision(),
                policy.commit(commitSha),
                "READY",
                existingById == null ? normalizedActor : existingById.createdBy(),
                existingById == null ? now : existingById.createdAt(),
                now);
        return transactions.required(() -> {
            SourceRepository saved = repositories.save(repository);
            mcpProjection.publish(saved);
            audit.record(new SourceAuditEvent(
                    saved.projectId(), "source-repository",
                    existingById == null ? "register" : "update",
                    saved.repositoryId(), existingById, saved));
            return saved;
        });
    }

    public List<SourceRepository> list(String projectId) {
        assertEnabled();
        return repositories.list(value(projectId));
    }

    public Optional<SourceRepository> find(String projectId, String repositoryId) {
        if (!enabled || value(projectId).isBlank() || value(repositoryId).isBlank()) return Optional.empty();
        return repositories.find(value(projectId), value(repositoryId));
    }

    public DeploymentRevision recordDeployment(DeploymentRevisionCandidate candidate, String actor) {
        assertEnabled();
        DeploymentRevisionCandidate normalized = policy.deployment(candidate);
        String normalizedActor = policy.actor(actor);
        SourceRepository repository = requireRepository(normalized.projectId(), normalized.repositoryId());
        String revision = normalized.revision().isBlank()
                ? repository.defaultRevision()
                : normalized.revision();
        String commitSha = policy.commit(resolveCommit(repository, revision));
        String now = now();
        DeploymentRevision deployment = new DeploymentRevision(
                policy.deploymentId(normalized.projectId(), normalized.environment(), normalized.serviceName()),
                normalized.projectId(),
                normalized.repositoryId(),
                normalized.environment(),
                normalized.serviceName(),
                commitSha,
                normalized.imageRef(),
                normalizedActor,
                now,
                now);
        return transactions.required(() -> {
            DeploymentRevision saved = deployments.save(deployment);
            audit.record(new SourceAuditEvent(
                    saved.projectId(), "deployment-revision", "record",
                    saved.deploymentId(), null, saved));
            return saved;
        });
    }

    public List<DeploymentRevision> listDeployments(String projectId, String environment) {
        assertEnabled();
        String project = value(projectId);
        String env = value(environment);
        if (!project.isBlank()) project = policy.projectId(project);
        if (!env.isBlank()) env = policy.environment(env);
        return deployments.list(project, env);
    }

    public Optional<DeploymentRevision> resolveDeployment(
            String projectId,
            String environment,
            String serviceName) {
        assertEnabled();
        return deployments.resolve(
                policy.projectId(projectId),
                policy.environment(environment),
                policy.serviceName(serviceName));
    }

    public SourceFile readFile(
            String projectId,
            String repositoryId,
            String revision,
            String path) {
        assertEnabled();
        SourceRepository repository = requireRepository(
                policy.projectId(projectId), policy.repositoryId(repositoryId));
        return git.readFile(
                repository,
                policy.revisionOrDefault(revision, repository.defaultCommitSha()),
                policy.sourcePath(path));
    }

    public List<SourceSearchHit> search(
            String projectId,
            String repositoryId,
            String revision,
            String query,
            int limit) {
        assertEnabled();
        SourceRepository repository = requireRepository(
                policy.projectId(projectId), policy.repositoryId(repositoryId));
        return git.search(
                repository,
                policy.revisionOrDefault(revision, repository.defaultCommitSha()),
                policy.searchQuery(query),
                policy.searchLimit(limit));
    }

    public Optional<SourceMcpRuntimeSpec> resolveMcp(String mcpId) {
        return resolveMcp("", mcpId);
    }

    public Optional<SourceMcpRuntimeSpec> resolveMcp(String projectId, String mcpId) {
        if (!enabled || value(mcpId).isBlank()) return Optional.empty();
        return repositories.findByMcpId(value(projectId), value(mcpId))
                .filter(SourceRepository::ready)
                .map(mcpRuntime::describe);
    }

    private SourceRepository requireRepository(String projectId, String repositoryId) {
        return repositories.find(projectId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("代码仓库不存在：" + repositoryId));
    }

    private void assertEnabled() {
        if (!enabled) {
            throw new IllegalStateException("代码仓库能力未启用，请配置 OPS_SOURCE_REPOSITORY_ENABLED=true");
        }
        if (!repositories.available() || !deployments.available()) {
            throw new IllegalStateException("代码仓库配置必须使用 MySQL 持久化");
        }
    }

    private String resolveCommit(SourceRepositoryCandidate repository) {
        return repository.accessMode() == SourceRepositoryAccessMode.LOCAL
                ? git.resolveCommit(repository.localPath(), repository.defaultRevision())
                : git.resolveCommit(repository, repository.defaultRevision());
    }

    private String resolveCommit(SourceRepository repository, String revision) {
        return repository.accessMode() == SourceRepositoryAccessMode.LOCAL
                ? git.resolveCommit(repository.localPath(), revision)
                : git.resolveCommit(repository, revision);
    }

    private String now() {
        return TIME.format(LocalDateTime.now());
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
