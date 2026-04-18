package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageVersion;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Verifies authoritative Repair Workspace state before approving a repair package. */
final class OpsChangePackageRepairApprovalVerifier {

    private final OpsRepairWorkspaceService repairWorkspaceService;
    private final OpsChangePackageApprovalProofService proofService;
    private final OpsChangePackageApprovalSnapshotReader snapshotReader;

    OpsChangePackageRepairApprovalVerifier(OpsRepairWorkspaceService repairWorkspaceService,
                                           OpsChangePackageApprovalProofService proofService,
                                           OpsChangePackageApprovalSnapshotReader snapshotReader) {
        this.repairWorkspaceService = repairWorkspaceService;
        this.proofService = proofService;
        this.snapshotReader = snapshotReader;
    }

    void verify(ChangePackageCurrent current,
                ChangePackageVersion version,
                Map<String, Object> snapshot) {
        if (repairWorkspaceService == null) {
            throw new IllegalStateException("GIT_BRANCH_REPAIR 审批需要 repair workspace 服务");
        }
        String workspaceId = require(
                snapshot.get("repairWorkspaceId"),
                "GIT_BRANCH_REPAIR 缺少 repairWorkspaceId");
        OpsRepairWorkspaceDTO workspace = repairWorkspaceService.get(workspaceId)
                .orElseThrow(() -> new IllegalStateException("repair workspace 不存在：" + workspaceId));
        requireEquals(workspace.getProjectId(), current.projectId(), "workspace projectId 不匹配");
        requireEquals(workspace.getServiceId(), text(snapshot.get("serviceId"), ""), "workspace serviceId 不匹配");
        requireEquals(workspace.getRepositoryId(), text(snapshot.get("repositoryId"), ""), "workspace repositoryId 不匹配");
        requireEquals(workspace.getBaseCommit(), text(snapshot.get("baseCommit"), ""), "workspace baseCommit 不匹配");
        if (!"VERIFIED".equals(workspace.getStatus())) {
            throw new IllegalStateException("repair workspace 未 VERIFIED：" + workspace.getStatus());
        }
        requireEquals(
                workspace.getVerifiedCommit(),
                require(snapshot.get("repairCommit"), "GIT_BRANCH_REPAIR 缺少 repairCommit"),
                "repairCommit 与 workspace 不一致");
        Map<String, Object> diff = repairWorkspaceService.computeRepairDiff(workspaceId);
        requireEquals(
                text(diff.get("diffHash"), ""),
                require(snapshot.get("diffHash"), "GIT_BRANCH_REPAIR 缺少 diffHash"),
                "diffHash 与 workspace 当前 diff 不一致");
        List<String> packageChangedFiles = snapshotReader.stringList(firstNonNull(
                snapshot.get("changedFiles"), snapshot.get("changedFilesJson")));
        List<String> workspaceChangedFiles = snapshotReader.stringList(diff.get("changedFiles"));
        if (!new LinkedHashSet<>(workspaceChangedFiles).equals(new LinkedHashSet<>(packageChangedFiles))) {
            throw new IllegalStateException("changedFiles 与 workspace 当前 diff 不一致");
        }
        String testProofHash = require(
                snapshot.get("testProofHash"),
                "GIT_BRANCH_REPAIR 缺少 testProofHash");
        String riskLevel = text(snapshot.get("riskLevel"), "MEDIUM");
        if (!proofService.hasRepairProof(
                current, version, riskLevel, testProofHash, workspaceId)) {
            throw new IllegalStateException(
                    "GIT_BRANCH_REPAIR 缺少可信 test proof：" + testProofHash);
        }
        String workspaceArtifactSha = text(workspace.getArtifactSha256(), "").toLowerCase();
        if (!workspaceArtifactSha.isBlank()) {
            String approvedDigest = require(snapshot.get("artifactDigest"),
                    "GIT_BRANCH_REPAIR 缺少 artifactDigest");
            String approvedSha = approvedDigest.toLowerCase().startsWith("sha256:")
                    ? approvedDigest.substring("sha256:".length()).toLowerCase()
                    : approvedDigest.toLowerCase();
            if (!approvedSha.matches("[a-f0-9]{64}")) {
                throw new IllegalStateException("GIT_BRANCH_REPAIR artifactDigest 格式无效");
            }
            requireEquals(workspaceArtifactSha, approvedSha,
                    "artifactDigest 与 VERIFIED workspace artifact 不一致");
        }
    }

    private void requireEquals(String left, String right, String message) {
        if (!text(left, "").equals(text(right, ""))) {
            throw new IllegalStateException(message);
        }
    }

    private String require(Object value, String message) {
        String normalized = text(value, "");
        if (normalized.isBlank()) throw new IllegalStateException(message);
        return normalized;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values == null ? new Object[0] : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
