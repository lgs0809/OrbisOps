package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;

import java.util.List;

public interface SourceGitPort {

    boolean allowedRootsConfigured();

    String resolveCommit(String localPath, String revision);

    default String resolveCommit(SourceRepositoryCandidate repository, String revision) {
        return resolveCommit(repository.localPath(), revision);
    }

    default String resolveCommit(SourceRepository repository, String revision) {
        return resolveCommit(repository.localPath(), revision);
    }

    SourceFile readFile(SourceRepository repository, String revision, String path);

    List<SourceSearchHit> search(SourceRepository repository, String revision, String query, int limit);
}
