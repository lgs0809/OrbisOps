package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.api.dto.OpsRepairWorkspaceDTO;
import cn.lgs.orbisops.trigger.ops.repair.OpsRepairWorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Hydrates repair package identity from the authoritative Repair Workspace. */
final class OpsPreparationRepairContextHydrator {

    private final OpsRepairWorkspaceService repairWorkspaces;

    OpsPreparationRepairContextHydrator(OpsRepairWorkspaceService repairWorkspaces) {
        this.repairWorkspaces = repairWorkspaces;
    }

    void hydrate(Map<String, Object> request, String projectId) {
        if (request == null) return;
        String workspaceId = text(request.get("repairWorkspaceId"));
        if (workspaceId.isBlank()) return;
        if (repairWorkspaces == null) {
            throw new IllegalStateException("REPAIR_WORKSPACE_SERVICE_UNAVAILABLE");
        }

        OpsRepairWorkspaceDTO workspace = repairWorkspaces.get(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("repair workspace 不存在：" + workspaceId));
        requireEquals(projectId, workspace.getProjectId(), "repair workspace projectId 不匹配");
        if (!"VERIFIED".equals(workspace.getStatus())) {
            throw new IllegalStateException("repair workspace 未 VERIFIED：" + workspace.getStatus());
        }

        Map<String, Object> diff = repairWorkspaces.computeRepairDiff(workspaceId);
        request.put("repairWorkspaceId", workspaceId);
        request.put("serviceId", text(workspace.getServiceId()));
        request.put("repositoryId", text(workspace.getRepositoryId()));
        request.put("baseCommit", text(workspace.getBaseCommit()));
        request.put("repairCommit", required(workspace.getVerifiedCommit(), "VERIFIED repair workspace 缺少 verifiedCommit"));
        request.put("diffHash", required(diff.get("diffHash"), "VERIFIED repair workspace 缺少 diffHash"));
        request.put("changedFiles", stringList(diff.get("changedFiles")));
        request.put("testCommand", text(workspace.getTestCommand()));
        String artifactSha = text(workspace.getArtifactSha256()).toLowerCase();
        if (!artifactSha.isBlank()) {
            if (!artifactSha.matches("[a-f0-9]{64}")) {
                throw new IllegalStateException("VERIFIED repair workspace artifact SHA-256 格式无效");
            }
            request.put("artifactDigest", "sha256:" + artifactSha);
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String normalized = text(item);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private void requireEquals(String expected, String actual, String message) {
        if (!text(expected).equals(text(actual))) throw new SecurityException(message);
    }

    private String required(Object value, String message) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalStateException(message);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
