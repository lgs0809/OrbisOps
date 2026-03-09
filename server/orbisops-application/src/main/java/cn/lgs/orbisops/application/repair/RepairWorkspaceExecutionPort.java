package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

import java.nio.file.Path;

public interface RepairWorkspaceExecutionPort {

    RepairWorkspace createAndVerify(RepairExecutionCommand command);

    RepairWorkspace enterWorktree(RepairWorktreeCommand command);

    Path worktreePath(String workspaceId);

    RepairDiffSnapshot computeDiff(RepairWorkspace workspace);

    RepairCommitResult commit(RepairWorkspace workspace, String message, String actor);

    RepairArtifactValidation validateArtifact(
            RepairWorkspace workspace,
            String artifactPath,
            String artifactSha256);

    RepairCleanupResult cleanup(
            RepairWorkspace workspace,
            SourceRepository repository,
            boolean forceRemoveWorktree);
}
