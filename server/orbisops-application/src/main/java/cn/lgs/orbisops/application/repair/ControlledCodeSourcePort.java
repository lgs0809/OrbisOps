package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;

import java.util.List;
import java.util.Optional;

public interface ControlledCodeSourcePort {

    SourceFile readFile(
            String projectId,
            String repositoryId,
            String revision,
            String path);

    List<SourceSearchHit> search(
            String projectId,
            String repositoryId,
            String revision,
            String query,
            int limit);

    Optional<SourceRepository> find(String projectId, String repositoryId);
}
