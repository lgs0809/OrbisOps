package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.application.repair.RepairWorkspaceApplicationService;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.trigger.application.repair.OpsRepairWorkspaceMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repair Workspace compatibility ACL. */
@Service
public class OpsRepairWorkspaceService {

    private final RepairWorkspaceApplicationService workspaces;
    private final OpsRepairWorkspaceMapper mapper;

    public OpsRepairWorkspaceService(
            RepairWorkspaceApplicationService workspaces,
            OpsRepairWorkspaceMapper mapper) {
        this.workspaces = workspaces;
        this.mapper = mapper;
    }

    public Map<String, Object> capabilities() {
        return mapper.capabilities(workspaces.capabilities());
    }

    public OpsRepairWorkspaceDTO createAndVerify(OpsRepairWorkspaceRequestDTO request, String actor) {
        return mapper.view(workspaces.createAndVerify(mapper.candidate(request), actor));
    }

    public Optional<OpsRepairWorkspaceDTO> get(String workspaceId) {
        return workspaces.find(workspaceId).map(mapper::view);
    }

    public Path worktreePath(String workspaceId) {
        return workspaces.worktreePath(workspaceId);
    }

    public WriterLease claimWriter(String workspaceId, String ownerId) {
        RepairWriterLease lease = workspaces.claimWriter(workspaceId, ownerId);
        return new WriterLease(
                lease.workspaceId(), lease.projectId(), lease.ownerId(), lease.leaseToken(),
                lease.fencingToken(), lease.expiresAt(), lease.stateVersion());
    }

    public void markDirty(String workspaceId, String ownerId) {
        workspaces.markDirty(workspaceId, ownerId);
    }

    public void markTesting(String workspaceId, String ownerId) {
        workspaces.markTesting(workspaceId, ownerId);
    }

    public void recordTestOutcome(String workspaceId, String ownerId, boolean passed) {
        workspaces.recordTestOutcome(workspaceId, ownerId, passed);
    }

    public boolean releaseWriter(String workspaceId, String ownerId) {
        return workspaces.releaseWriter(workspaceId, ownerId);
    }

    public OpsRepairWorkspaceDTO enterWorktree(
            String projectId,
            String serviceId,
            String repositoryId,
            String environment,
            String baseCommit,
            String actor) {
        return mapper.view(workspaces.enterWorktree(
                projectId, serviceId, repositoryId, environment, baseCommit, actor));
    }

    public Map<String, Object> computeRepairDiff(String workspaceId) {
        return mapper.view(workspaces.computeDiff(workspaceId));
    }

    public Map<String, Object> commitRepair(String workspaceId, String message, String actor) {
        return commitRepair(workspaceId, message, actor, actor);
    }

    public Map<String, Object> commitRepair(
            String workspaceId,
            String message,
            String actor,
            String writerId) {
        return mapper.view(workspaces.commitRepair(workspaceId, message, actor, writerId));
    }

    public Map<String, Object> verifyRepairWorkspace(
            String workspaceId,
            String expectedDiffHash,
            List<String> expectedChangedFiles,
            String testProofHash,
            String actor) {
        return mapper.view(workspaces.verify(
                workspaceId, expectedDiffHash, expectedChangedFiles, testProofHash, actor));
    }

    public List<OpsRepairWorkspaceDTO> list(String projectId) {
        return mapper.views(workspaces.list(projectId));
    }

    public Map<String, Object> validateArtifact(
            String workspaceId,
            String projectId,
            String serviceId,
            String artifactPath,
            String artifactSha256) {
        return mapper.view(workspaces.validateArtifact(
                workspaceId, projectId, serviceId, artifactPath, artifactSha256));
    }

    public void deleteWorktree(String workspaceId) {
        workspaces.deleteWorktree(workspaceId);
    }

    public Map<String, Object> cleanupRepairWorkspace(String workspaceId, boolean forceRemoveWorktree) {
        return mapper.view(workspaces.cleanupIfPresent(workspaceId, forceRemoveWorktree));
    }

    public record WriterLease(
            String workspaceId,
            String projectId,
            String ownerId,
            String leaseToken,
            long fencingToken,
            String expiresAt,
            long stateVersion) {
    }
}
