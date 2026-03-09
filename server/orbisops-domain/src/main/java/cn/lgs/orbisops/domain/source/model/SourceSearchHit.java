package cn.lgs.orbisops.domain.source.model;

public record SourceSearchHit(
        String repositoryId,
        String commitSha,
        String path,
        int line,
        String text) {

    public SourceSearchHit {
        repositoryId = required(repositoryId, "SOURCE_REPOSITORY_ID_REQUIRED");
        commitSha = required(commitSha, "SOURCE_SEARCH_COMMIT_REQUIRED").toLowerCase();
        path = required(path, "SOURCE_SEARCH_PATH_REQUIRED");
        line = Math.max(line, 0);
        text = text == null ? "" : text;
    }

    private static String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
