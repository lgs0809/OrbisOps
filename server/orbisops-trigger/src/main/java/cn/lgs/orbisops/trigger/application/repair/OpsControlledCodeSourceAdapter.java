package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeSourcePort;
import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OpsControlledCodeSourceAdapter implements ControlledCodeSourcePort {

    private final SourceRepositoryApplicationService sources;

    public OpsControlledCodeSourceAdapter(SourceRepositoryApplicationService sources) {
        this.sources = sources;
    }

    @Override
    public SourceFile readFile(
            String projectId,
            String repositoryId,
            String revision,
            String path) {
        return sources.readFile(projectId, repositoryId, revision, path);
    }

    @Override
    public List<SourceSearchHit> search(
            String projectId,
            String repositoryId,
            String revision,
            String query,
            int limit) {
        return sources.search(projectId, repositoryId, revision, query, limit);
    }

    @Override
    public Optional<SourceRepository> find(String projectId, String repositoryId) {
        return sources.find(projectId, repositoryId);
    }
}
