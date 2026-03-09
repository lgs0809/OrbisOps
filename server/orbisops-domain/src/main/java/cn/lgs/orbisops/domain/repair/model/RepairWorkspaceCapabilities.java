package cn.lgs.orbisops.domain.repair.model;

import java.util.List;

public record RepairWorkspaceCapabilities(
        boolean enabled,
        String isolation,
        String buildRunner,
        boolean networkAccess,
        boolean arbitraryShellAllowed,
        List<String> buildProfiles,
        int maxPatchBytes,
        int maxChangedFiles) {

    public RepairWorkspaceCapabilities {
        isolation = isolation == null ? "GIT_WORKTREE" : isolation.trim();
        buildRunner = buildRunner == null ? "DOCKER" : buildRunner.trim().toUpperCase();
        buildProfiles = buildProfiles == null ? List.of() : List.copyOf(buildProfiles);
        maxPatchBytes = Math.max(1, maxPatchBytes);
        maxChangedFiles = Math.max(1, maxChangedFiles);
    }
}
