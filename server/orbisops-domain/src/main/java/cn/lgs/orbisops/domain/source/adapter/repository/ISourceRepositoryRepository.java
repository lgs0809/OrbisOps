package cn.lgs.orbisops.domain.source.adapter.repository;

import cn.lgs.orbisops.domain.source.model.SourceRepository;

import java.util.List;
import java.util.Optional;

public interface ISourceRepositoryRepository {

    boolean available();

    SourceRepository save(SourceRepository repository);

    Optional<SourceRepository> find(String projectId, String repositoryId);

    Optional<SourceRepository> findByRepositoryId(String repositoryId);

    Optional<SourceRepository> findByMcpId(String projectId, String mcpId);

    List<SourceRepository> list(String projectId);
}
