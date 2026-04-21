package cn.lgs.orbisops.domain.repair.model;

import java.util.List;

public record RepairArtifactValidation(
        String workspaceId,
        String artifactPath,
        String artifactSha256,
        long artifactSize,
        String baseCommit,
        String verifiedCommit,
        List<String> changedFiles,
        String testProfile,
        Integer testExitCode) {

    public RepairArtifactValidation {
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        artifactPath = required(artifactPath, "REPAIR_ARTIFACT_PATH_REQUIRED");
        artifactSha256 = required(artifactSha256, "REPAIR_ARTIFACT_SHA_REQUIRED").toLowerCase();
        if (!artifactSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("REPAIR_ARTIFACT_SHA_INVALID");
        artifactSize = Math.max(0, artifactSize);
        baseCommit = required(baseCommit, "REPAIR_BASE_COMMIT_REQUIRED").toLowerCase();
        verifiedCommit = value(verifiedCommit).toLowerCase();
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        testProfile = value(testProfile);
    }

    private static String required(String value, String error) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
