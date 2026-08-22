package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairExecutionCommand;
import cn.lgs.orbisops.application.repair.RepairSourceCatalogPort;
import cn.lgs.orbisops.application.repair.RepairWorkspaceExecutionPort;
import cn.lgs.orbisops.application.repair.RepairWorktreeCommand;
import cn.lgs.orbisops.domain.repair.adapter.repository.IRepairWorkspaceRepository;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Routes the existing RepairWorkspace port without leaking MCP types into application/domain. */
@Primary
@Component
public final class RoutingRepairWorkspaceExecutionAdapter implements RepairWorkspaceExecutionPort {

    private final RepairWorkspaceExecutionPort local;
    private final RepairWorkspaceExecutionPort remote;
    private final RepairSourceCatalogPort sources;
    private final IRepairWorkspaceRepository workspaces;

    public RoutingRepairWorkspaceExecutionAdapter(
            @Qualifier("localRepairWorkspaceExecutionAdapter") RepairWorkspaceExecutionPort local,
            @Qualifier("mcpRepairWorkspaceExecutionAdapter") RepairWorkspaceExecutionPort remote,
            RepairSourceCatalogPort sources,
            IRepairWorkspaceRepository workspaces) {
        this.local = local;
        this.remote = remote;
        this.sources = sources;
        this.workspaces = workspaces;
    }

    @Override
    public RepairWorkspace createAndVerify(RepairExecutionCommand command) {
        return isRemote(command == null ? null : command.repository())
                ? remote.createAndVerify(command)
                : local.createAndVerify(command);
    }

    @Override
    public RepairWorkspace enterWorktree(RepairWorktreeCommand command) {
        return isRemote(command == null ? null : command.repository())
                ? remote.enterWorktree(command)
                : local.enterWorktree(command);
    }

    @Override
    public Path worktreePath(String workspaceId) {
        RepairWorkspace workspace = workspace(workspaceId);
        return isRemote(repository(workspace))
                ? remote.worktreePath(workspaceId)
                : local.worktreePath(workspaceId);
    }

    @Override
    public RepairDiffSnapshot computeDiff(RepairWorkspace workspace) {
        return isRemote(repository(workspace))
                ? remote.computeDiff(workspace)
                : local.computeDiff(workspace);
    }

    @Override
    public RepairCommitResult commit(RepairWorkspace workspace, String message, String actor) {
        return isRemote(repository(workspace))
                ? remote.commit(workspace, message, actor)
                : local.commit(workspace, message, actor);
    }

    @Override
    public RepairArtifactValidation validateArtifact(
            RepairWorkspace workspace,
            String artifactPath,
            String artifactSha256) {
        return isRemote(repository(workspace))
                ? remote.validateArtifact(workspace, artifactPath, artifactSha256)
                : local.validateArtifact(workspace, artifactPath, artifactSha256);
    }

    @Override
    public RepairCleanupResult cleanup(
            RepairWorkspace workspace,
            SourceRepository repository,
            boolean forceRemoveWorktree) {
        SourceRepository source = repository == null ? repository(workspace) : repository;
        return isRemote(source)
                ? remote.cleanup(workspace, source, forceRemoveWorktree)
                : local.cleanup(workspace, source, forceRemoveWorktree);
    }

    private RepairWorkspace workspace(String workspaceId) {
        return workspaces.find(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("修复工作区不存在：" + workspaceId));
    }

    private SourceRepository repository(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        return sources.findRepository(workspace.projectId(), workspace.repositoryId())
                .orElseThrow(() -> new IllegalArgumentException("代码仓库不存在：" + workspace.repositoryId()));
    }

    private boolean isRemote(SourceRepository repository) {
        return repository != null && repository.accessMode() == SourceRepositoryAccessMode.MCP;
    }
}
