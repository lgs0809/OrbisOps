package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceRequestDTO;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairVerificationResult;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCapabilities;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpsRepairWorkspaceMapper {

    public RepairWorkspaceCandidate candidate(OpsRepairWorkspaceRequestDTO request) {
        if (request == null) return null;
        return new RepairWorkspaceCandidate(
                request.getProjectId(), request.getServiceId(), request.getEnvironment(),
                request.getSummary(), request.getUnifiedDiff(), request.getBaseCommit());
    }

    public OpsRepairWorkspaceDTO view(RepairWorkspace workspace) {
        if (workspace == null) return null;
        return OpsRepairWorkspaceDTO.builder()
                .workspaceId(workspace.workspaceId())
                .projectId(workspace.projectId())
                .serviceId(workspace.serviceId())
                .repositoryId(workspace.repositoryId())
                .environment(workspace.environment())
                .baseCommit(workspace.baseCommit())
                .verifiedCommit(workspace.verifiedCommit())
                .status(workspace.status().name())
                .summary(workspace.summary())
                .unifiedDiff(workspace.unifiedDiff())
                .changedFiles(workspace.changedFiles())
                .testProfile(workspace.testProfile())
                .testCommand(workspace.testCommand())
                .testExitCode(workspace.testExitCode())
                .testLog(workspace.testLog())
                .artifactPath(workspace.artifactPath())
                .artifactSha256(workspace.artifactSha256())
                .artifactSize(workspace.artifactSize())
                .createdBy(workspace.createdBy())
                .createdAt(workspace.createdAt())
                .updatedAt(workspace.updatedAt())
                .build();
    }

    public List<OpsRepairWorkspaceDTO> views(List<RepairWorkspace> values) {
        return values == null ? List.of() : values.stream().map(this::view).toList();
    }

    public Map<String, Object> capabilities(RepairWorkspaceCapabilities capabilities) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", capabilities.enabled());
        result.put("isolation", capabilities.isolation());
        result.put("buildRunner", capabilities.buildRunner());
        result.put("networkAccess", capabilities.networkAccess());
        result.put("arbitraryShellAllowed", capabilities.arbitraryShellAllowed());
        result.put("buildProfiles", capabilities.buildProfiles());
        result.put("maxPatchBytes", capabilities.maxPatchBytes());
        result.put("maxChangedFiles", capabilities.maxChangedFiles());
        return result;
    }

    public Map<String, Object> view(RepairDiffSnapshot diff) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", diff.workspaceId());
        result.put("baseCommit", diff.baseCommit());
        result.put("currentHead", diff.currentHead());
        result.put("changedFiles", diff.changedFiles());
        result.put("diffSummary", diff.diffSummary());
        result.put("diffHash", diff.diffHash());
        result.put("diffBytes", diff.diffBytes());
        return result;
    }

    public Map<String, Object> view(RepairCommitResult commit) {
        Map<String, Object> result = new LinkedHashMap<>(view(commit.diff()));
        result.put("repairCommit", commit.repairCommit());
        result.put("status", commit.status().name());
        result.put("createdBy", commit.createdBy());
        return result;
    }

    public Map<String, Object> view(RepairVerificationResult verification) {
        Map<String, Object> result = new LinkedHashMap<>(view(verification.diff()));
        result.put("workspaceId", verification.diff().workspaceId());
        result.put("repairCommit", verification.repairCommit());
        result.put("testProofHash", verification.testProofHash());
        result.put("status", verification.status().name());
        result.put("verifiedBy", verification.verifiedBy());
        return result;
    }

    public Map<String, Object> view(RepairArtifactValidation artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", artifact.workspaceId());
        result.put("artifactPath", artifact.artifactPath());
        result.put("artifactSha256", artifact.artifactSha256());
        result.put("artifactSize", artifact.artifactSize());
        result.put("baseCommit", artifact.baseCommit());
        result.put("verifiedCommit", artifact.verifiedCommit());
        result.put("changedFiles", artifact.changedFiles());
        result.put("testProfile", artifact.testProfile());
        result.put("testExitCode", artifact.testExitCode());
        return result;
    }

    public Map<String, Object> view(RepairCleanupResult cleanup) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", cleanup.workspaceId());
        result.put("status", cleanup.status());
        result.put("removed", cleanup.removed());
        if (!cleanup.worktreePath().isBlank()) result.put("worktreePath", cleanup.worktreePath());
        return result;
    }
}
