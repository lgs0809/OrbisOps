package cn.lgs.orbisops.domain.source.model;

public record SourceRepositoryCandidate(
        String projectId,
        String repositoryId,
        String name,
        String localPath,
        String defaultRevision,
        SourceRepositoryAccessMode accessMode,
        String codeMcpId,
        String logicalRoot) {

    public SourceRepositoryCandidate(
            String projectId,
            String repositoryId,
            String name,
            String localPath,
            String defaultRevision) {
        this(projectId, repositoryId, name, localPath, defaultRevision,
                SourceRepositoryAccessMode.LOCAL, "", "");
    }
}
