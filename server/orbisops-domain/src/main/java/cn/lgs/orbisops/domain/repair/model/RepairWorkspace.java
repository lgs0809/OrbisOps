package cn.lgs.orbisops.domain.repair.model;

import java.util.List;

public record RepairWorkspace(
        String workspaceId,
        String projectId,
        String serviceId,
        String repositoryId,
        String environment,
        String baseCommit,
        String verifiedCommit,
        RepairWorkspaceStatus status,
        String summary,
        String unifiedDiff,
        List<String> changedFiles,
        String testProfile,
        String testCommand,
        Integer testExitCode,
        String testLog,
        String artifactPath,
        String artifactSha256,
        Long artifactSize,
        String createdBy,
        String createdAt,
        String updatedAt) {

    public RepairWorkspace {
        workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        projectId = required(projectId, "REPAIR_PROJECT_ID_REQUIRED");
        serviceId = required(serviceId, "REPAIR_SERVICE_ID_REQUIRED");
        repositoryId = required(repositoryId, "REPAIR_REPOSITORY_ID_REQUIRED");
        environment = required(environment, "REPAIR_ENVIRONMENT_REQUIRED").toLowerCase();
        baseCommit = commit(baseCommit, "REPAIR_BASE_COMMIT_REQUIRED");
        verifiedCommit = optionalCommit(verifiedCommit);
        if (status == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_STATUS_REQUIRED");
        summary = required(summary, "REPAIR_SUMMARY_REQUIRED");
        unifiedDiff = value(unifiedDiff);
        changedFiles = changedFiles == null ? List.of() : changedFiles.stream()
                .map(RepairWorkspace::value)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        testProfile = value(testProfile);
        testCommand = value(testCommand);
        testLog = value(testLog);
        artifactPath = value(artifactPath);
        artifactSha256 = value(artifactSha256).toLowerCase();
        artifactSize = artifactSize == null ? 0L : Math.max(0L, artifactSize);
        createdBy = required(createdBy, "REPAIR_ACTOR_REQUIRED");
        createdAt = required(createdAt, "REPAIR_CREATED_AT_REQUIRED");
        updatedAt = required(updatedAt, "REPAIR_UPDATED_AT_REQUIRED");
    }

    public RepairWorkspace withStatus(RepairWorkspaceStatus next, String updatedAt) {
        return new RepairWorkspace(
                workspaceId, projectId, serviceId, repositoryId, environment, baseCommit, verifiedCommit,
                next, summary, unifiedDiff, changedFiles, testProfile, testCommand, testExitCode, testLog,
                artifactPath, artifactSha256, artifactSize, createdBy, createdAt, updatedAt);
    }

    public RepairWorkspace committed(String commit, List<String> files, String updatedAt) {
        return new RepairWorkspace(
                workspaceId, projectId, serviceId, repositoryId, environment, baseCommit, commit,
                RepairWorkspaceStatus.COMMITTED, summary, unifiedDiff, files, testProfile, testCommand,
                testExitCode, testLog, artifactPath, artifactSha256, artifactSize, createdBy, createdAt, updatedAt);
    }

    public RepairWorkspace verified(List<String> files, String updatedAt) {
        return new RepairWorkspace(
                workspaceId, projectId, serviceId, repositoryId, environment, baseCommit, verifiedCommit,
                RepairWorkspaceStatus.VERIFIED, summary, unifiedDiff, files, testProfile, testCommand,
                testExitCode, testLog, artifactPath, artifactSha256, artifactSize, createdBy, createdAt, updatedAt);
    }

    private static String optionalCommit(String input) {
        String normalized = value(input);
        return normalized.isBlank() ? "" : commit(normalized, "REPAIR_VERIFIED_COMMIT_INVALID");
    }

    private static String commit(String input, String error) {
        String normalized = value(input).toLowerCase();
        if (!normalized.matches("[a-f0-9]{40}")) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String required(String input, String error) {
        String normalized = value(input);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
