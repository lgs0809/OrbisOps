package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.source.SourceGitPort;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Selects the infrastructure adapter from the repository's persisted access mode. */
@Primary
@Component
public final class RoutingSourceGitAdapter implements SourceGitPort {

    private final SourceGitPort local;
    private final SourceGitPort remote;

    public RoutingSourceGitAdapter(
            @Qualifier("localGitSourceGateway") SourceGitPort local,
            @Qualifier("mcpSourceGitAdapter") SourceGitPort remote) {
        if (local == null) throw new IllegalArgumentException("LOCAL_SOURCE_GIT_ADAPTER_REQUIRED");
        if (remote == null) throw new IllegalArgumentException("MCP_SOURCE_GIT_ADAPTER_REQUIRED");
        this.local = local;
        this.remote = remote;
    }

    @Override
    public boolean allowedRootsConfigured() {
        return local.allowedRootsConfigured();
    }

    @Override
    public String resolveCommit(String localPath, String revision) {
        return local.resolveCommit(localPath, revision);
    }

    @Override
    public String resolveCommit(SourceRepositoryCandidate repository, String revision) {
        return mode(repository == null ? null : repository.accessMode()) == SourceRepositoryAccessMode.MCP
                ? remote.resolveCommit(repository, revision)
                : local.resolveCommit(repository.localPath(), revision);
    }

    @Override
    public String resolveCommit(SourceRepository repository, String revision) {
        return mode(repository == null ? null : repository.accessMode()) == SourceRepositoryAccessMode.MCP
                ? remote.resolveCommit(repository, revision)
                : local.resolveCommit(repository.localPath(), revision);
    }

    @Override
    public SourceFile readFile(SourceRepository repository, String revision, String path) {
        return mode(repository == null ? null : repository.accessMode()) == SourceRepositoryAccessMode.MCP
                ? remote.readFile(repository, revision, path)
                : local.readFile(repository, revision, path);
    }

    @Override
    public List<SourceSearchHit> search(SourceRepository repository, String revision, String query, int limit) {
        return mode(repository == null ? null : repository.accessMode()) == SourceRepositoryAccessMode.MCP
                ? remote.search(repository, revision, query, limit)
                : local.search(repository, revision, query, limit);
    }

    private SourceRepositoryAccessMode mode(SourceRepositoryAccessMode mode) {
        return mode == null ? SourceRepositoryAccessMode.LOCAL : mode;
    }
}
