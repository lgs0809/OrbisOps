package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.adapter.repository.IRepairWorkspaceRepository;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairVerificationResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCapabilities;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.domain.repair.service.RepairWorkspacePolicy;
import cn.lgs.orbisops.domain.source.model.DeploymentRevision;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RepairWorkspaceApplicationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IRepairWorkspaceRepository workspaces;
    private final RepairSourceCatalogPort sources;
    private final RepairWorkspaceExecutionPort execution;
    private final RepairAuditPort audit;
    private final RepairTransactionPort transactions;
    private final RepairWorkspacePolicy policy;
    private final boolean enabled;
    private final String runner;
    private final long writerLeaseSeconds;

    public RepairWorkspaceApplicationService(
            IRepairWorkspaceRepository workspaces,
            RepairSourceCatalogPort sources,
            RepairWorkspaceExecutionPort execution,
            RepairAuditPort audit,
            RepairTransactionPort transactions,
            boolean enabled,
            String runner,
            long writerLeaseSeconds) {
        this(workspaces, sources, execution, audit, transactions, enabled, runner,
                writerLeaseSeconds, new RepairWorkspacePolicy());
    }

    RepairWorkspaceApplicationService(
            IRepairWorkspaceRepository workspaces,
            RepairSourceCatalogPort sources,
            RepairWorkspaceExecutionPort execution,
            RepairAuditPort audit,
            RepairTransactionPort transactions,
            boolean enabled,
            String runner,
            long writerLeaseSeconds,
            RepairWorkspacePolicy policy) {
        if (workspaces == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REPOSITORY_REQUIRED");
        if (sources == null) throw new IllegalArgumentException("REPAIR_SOURCE_CATALOG_REQUIRED");
        if (execution == null) throw new IllegalArgumentException("REPAIR_EXECUTION_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("REPAIR_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("REPAIR_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_POLICY_REQUIRED");
        this.workspaces = workspaces;
        this.sources = sources;
        this.execution = execution;
        this.audit = audit;
        this.transactions = transactions;
        this.enabled = enabled;
        this.runner = value(runner).isBlank() ? "docker" : value(runner);
        this.writerLeaseSeconds = Math.max(30L, writerLeaseSeconds);
        this.policy = policy;
    }

    public RepairWorkspaceCapabilities capabilities() {
        return new RepairWorkspaceCapabilities(
                enabled,
                "GIT_WORKTREE",
                runner,
                false,
                false,
                Arrays.stream(cn.lgs.orbisops.domain.source.model.BuildProfile.values()).map(Enum::name).toList(),
                RepairWorkspacePolicy.MAX_PATCH_BYTES,
                RepairWorkspacePolicy.MAX_CHANGED_FILES);
    }

    public RepairWorkspace createAndVerify(RepairWorkspaceCandidate candidate, String actor) {
        assertEnabled();
        RepairWorkspaceCandidate normalized = policy.normalize(candidate);
        String operator = policy.actor(actor);
        ProjectService service = requireService(normalized.projectId(), normalized.serviceId());
        SourceRepository repository = requireRepository(normalized.projectId(), service.repositoryId());
        policy.validatePatch(normalized.unifiedDiff(), service.modulePath());
        String baseCommit = normalized.baseCommit();
        if (baseCommit.isBlank()) {
            baseCommit = sources.resolveDeployment(
                            normalized.projectId(), normalized.environment(), normalized.serviceId())
                    .map(DeploymentRevision::commitSha)
                    .orElse(repository.defaultCommitSha());
        }
        baseCommit = policy.commit(baseCommit);
        String workspaceId = workspaceId();
        RepairWorkspace result = execution.createAndVerify(new RepairExecutionCommand(
                workspaceId, normalized, service, repository, baseCommit, operator));
        return transactions.required(() -> {
            RepairWorkspace saved = workspaces.save(result);
            audit.record(new RepairAuditEvent(
                    saved.projectId(), "create", saved.workspaceId(), null, saved));
            return saved;
        });
    }

    public List<RepairWorkspace> list(String projectId) {
        return workspaces.list(value(projectId));
    }

    public Optional<RepairWorkspace> find(String workspaceId) {
        String id = value(workspaceId);
        return id.isBlank() ? Optional.empty() : workspaces.find(id);
    }

    public RepairWorkspace get(String workspaceId) {
        String id = policy.workspaceId(workspaceId);
        return workspaces.find(id)
                .orElseThrow(() -> new IllegalArgumentException("REPAIR_WORKSPACE_NOT_FOUND:" + id));
    }

    public Path worktreePath(String workspaceId) {
        get(workspaceId);
        return execution.worktreePath(policy.workspaceId(workspaceId));
    }

    public RepairWriterLease claimWriter(String workspaceId, String ownerId) {
        return workspaces.claimWriter(
                policy.workspaceId(workspaceId), policy.actor(ownerId), writerLeaseSeconds);
    }

    public void markDirty(String workspaceId, String ownerId) {
        updateWriterStatus(workspaceId, ownerId, RepairWorkspaceStatus.DIRTY);
    }

    public void markTesting(String workspaceId, String ownerId) {
        updateWriterStatus(workspaceId, ownerId, RepairWorkspaceStatus.TESTING);
    }

    public void recordTestOutcome(String workspaceId, String ownerId, boolean passed) {
        updateWriterStatus(
                workspaceId, ownerId,
                passed ? RepairWorkspaceStatus.TEST_PASSED : RepairWorkspaceStatus.FAILED);
    }

    public boolean releaseWriter(String workspaceId, String ownerId) {
        return workspaces.releaseWriter(policy.workspaceId(workspaceId), policy.actor(ownerId));
    }

    public RepairWorkspace enterWorktree(
            String projectId,
            String serviceId,
            String repositoryId,
            String environment,
            String baseCommit,
            String actor) {
        assertEnabled();
        String project = required(projectId, "projectId");
        String serviceKey = required(serviceId, "serviceId");
        String operator = policy.actor(actor);
        ProjectService service = requireService(project, serviceKey);
        String repositoryKey = value(repositoryId).isBlank() ? service.repositoryId() : value(repositoryId);
        SourceRepository repository = requireRepository(project, repositoryKey);
        String commit = policy.commit(baseCommit);
        RepairWorkspace workspace = execution.enterWorktree(new RepairWorktreeCommand(
                workspaceId(), project, serviceKey,
                value(environment).isBlank() ? "dev" : value(environment).toLowerCase(),
                commit, operator, service, repository));
        return transactions.required(() -> {
            RepairWorkspace saved = workspaces.save(workspace);
            audit.record(new RepairAuditEvent(
                    saved.projectId(), "enter-worktree", saved.workspaceId(), null, saved));
            return saved;
        });
    }

    public RepairDiffSnapshot computeDiff(String workspaceId) {
        return execution.computeDiff(get(workspaceId));
    }

    public RepairCommitResult commitRepair(
            String workspaceId,
            String message,
            String actor,
            String writerId) {
        String operator = policy.actor(actor);
        claimWriter(workspaceId, writerId);
        RepairWorkspace workspace = get(workspaceId);
        RepairDiffSnapshot diff = execution.computeDiff(workspace);
        policy.requireCommitAllowed(workspace, diff);
        RepairCommitResult result = execution.commit(workspace, value(message), operator);
        RepairWorkspace updated = workspace.committed(
                result.repairCommit(), result.diff().changedFiles(), now());
        return transactions.required(() -> {
            workspaces.update(updated);
            audit.record(new RepairAuditEvent(
                    updated.projectId(), "commit", updated.workspaceId(), workspace, result));
            return result;
        });
    }

    public RepairVerificationResult verify(
            String workspaceId,
            String expectedDiffHash,
            List<String> expectedChangedFiles,
            String testProofHash,
            String actor) {
        String operator = policy.actor(actor);
        RepairWorkspace workspace = get(workspaceId);
        RepairDiffSnapshot diff = execution.computeDiff(workspace);
        policy.requireVerification(
                workspace, diff, expectedDiffHash, expectedChangedFiles, testProofHash);
        RepairWorkspace updated = workspace.verified(diff.changedFiles(), now());
        RepairVerificationResult result = new RepairVerificationResult(
                diff, updated.verifiedCommit(), testProofHash,
                RepairWorkspaceStatus.VERIFIED, operator);
        return transactions.required(() -> {
            workspaces.update(updated);
            audit.record(new RepairAuditEvent(
                    updated.projectId(), "verify", updated.workspaceId(), workspace, result));
            return result;
        });
    }

    public RepairArtifactValidation validateArtifact(
            String workspaceId,
            String projectId,
            String serviceId,
            String artifactPath,
            String artifactSha256) {
        RepairWorkspace workspace = get(workspaceId);
        policy.requireArtifact(workspace, projectId, serviceId, artifactPath, artifactSha256);
        return execution.validateArtifact(workspace, artifactPath, artifactSha256);
    }

    public RepairCleanupResult cleanup(String workspaceId, String actor) {
        String operator = policy.actor(actor);
        RepairWorkspace workspace = get(workspaceId);
        policy.requireCleanupAllowed(workspace);
        SourceRepository repository = sources.findRepository(
                        workspace.projectId(), workspace.repositoryId())
                .orElse(null);
        RepairCleanupResult result = execution.cleanup(workspace, repository, true);
        RepairWorkspace updated = workspace.withStatus(RepairWorkspaceStatus.CLEANED, now());
        return transactions.required(() -> {
            workspaces.update(updated);
            audit.record(new RepairAuditEvent(
                    updated.projectId(), "cleanup", updated.workspaceId(), workspace,
                    new CleanupAuditResult(result, operator)));
            return result;
        });
    }

    public RepairCleanupResult cleanupIfPresent(String workspaceId, boolean forceRemoveWorktree) {
        String id = policy.workspaceId(workspaceId);
        RepairWorkspace workspace = workspaces.find(id).orElse(null);
        if (workspace == null) {
            return new RepairCleanupResult(id, "NO_TEMP_RESOURCE", false, "");
        }
        SourceRepository repository = sources.findRepository(
                        workspace.projectId(), workspace.repositoryId())
                .orElse(null);
        RepairCleanupResult result = execution.cleanup(workspace, repository, forceRemoveWorktree);
        workspaces.update(workspace.withStatus(RepairWorkspaceStatus.CLEANED, now()));
        return result;
    }

    public void deleteWorktree(String workspaceId) {
        cleanupIfPresent(workspaceId, true);
    }

    private void updateWriterStatus(
            String workspaceId,
            String ownerId,
            RepairWorkspaceStatus status) {
        workspaces.updateWriterOwnedStatus(
                policy.workspaceId(workspaceId), policy.actor(ownerId), status);
    }

    private ProjectService requireService(String projectId, String serviceId) {
        return sources.findService(projectId, serviceId)
                .orElseThrow(() -> new IllegalArgumentException("项目服务不存在：" + serviceId));
    }

    private SourceRepository requireRepository(String projectId, String repositoryId) {
        return sources.findRepository(projectId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("服务代码仓库不存在：" + repositoryId));
    }

    private void assertEnabled() {
        if (!enabled) throw new IllegalStateException("代码修复沙箱未启用");
    }

    private String workspaceId() {
        return "repair_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String required(String value, String field) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private String now() {
        return TIME.format(LocalDateTime.now());
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    public record CleanupAuditResult(RepairCleanupResult cleanup, String actor) {
    }
}
