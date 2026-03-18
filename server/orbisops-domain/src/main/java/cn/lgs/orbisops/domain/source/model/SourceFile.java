package cn.lgs.orbisops.domain.source.model;

public record SourceFile(
        String repositoryId,
        String commitSha,
        String path,
        long sizeBytes,
        String content) {

    public SourceFile {
        repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
        commitSha = required(commitSha, "SOURCE_FILE_COMMIT_REQUIRED").toLowerCase();
        path = required(path, "SOURCE_FILE_PATH_REQUIRED");
        if (sizeBytes < 0) throw new IllegalArgumentException("SOURCE_FILE_SIZE_INVALID:" + sizeBytes);
        content = content == null ? "" : content;
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
