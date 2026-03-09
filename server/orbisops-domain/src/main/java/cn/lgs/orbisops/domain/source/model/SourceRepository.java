package cn.lgs.orbisops.domain.source.model;

public record SourceRepository(
        String repositoryId,
        String mcpId,
        String projectId,
        String name,
        String localPath,
        SourceRepositoryAccessMode accessMode,
        String codeMcpId,
        String logicalRoot,
        String defaultRevision,
        String defaultCommitSha,
        String status,
        String createdBy,
        String createdAt,
        String updatedAt) {

    public SourceRepository {
        repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
        mcpId = required(mcpId, "SOURCE_REPOSITORY_MCP_ID_REQUIRED");
        projectId = required(projectId, "SOURCE_PROJECT_ID_REQUIRED");
        name = required(name, "SOURCE_REPOSITORY_NAME_REQUIRED");
        accessMode = accessMode == null ? SourceRepositoryAccessMode.LOCAL : accessMode;
        localPath = value(localPath);
        codeMcpId = value(codeMcpId);
        logicalRoot = value(logicalRoot);
        if (accessMode == SourceRepositoryAccessMode.LOCAL) {
            localPath = required(localPath, "SOURCE_REPOSITORY_LOCAL_PATH_REQUIRED");
        } else {
            codeMcpId = required(codeMcpId, "SOURCE_REPOSITORY_CODE_MCP_ID_REQUIRED");
            logicalRoot = required(logicalRoot, "SOURCE_REPOSITORY_LOGICAL_ROOT_REQUIRED");
        }
        defaultRevision = required(defaultRevision, "SOURCE_REPOSITORY_REVISION_REQUIRED");
        defaultCommitSha = required(defaultCommitSha, "SOURCE_REPOSITORY_COMMIT_REQUIRED").toLowerCase();
        status = required(status, "SOURCE_REPOSITORY_STATUS_REQUIRED").toUpperCase();
        createdBy = required(createdBy, "SOURCE_REPOSITORY_ACTOR_REQUIRED");
        createdAt = required(createdAt, "SOURCE_REPOSITORY_CREATED_AT_REQUIRED");
        updatedAt = required(updatedAt, "SOURCE_REPOSITORY_UPDATED_AT_REQUIRED");
    }

    public SourceRepository(
            String repositoryId,
            String mcpId,
            String projectId,
            String name,
            String localPath,
            String defaultRevision,
            String defaultCommitSha,
            String status,
            String createdBy,
            String createdAt,
            String updatedAt) {
        this(repositoryId, mcpId, projectId, name, localPath,
                SourceRepositoryAccessMode.LOCAL, "", "",
                defaultRevision, defaultCommitSha, status, createdBy, createdAt, updatedAt);
    }

    public boolean ready() {
        return "READY".equals(status);
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
