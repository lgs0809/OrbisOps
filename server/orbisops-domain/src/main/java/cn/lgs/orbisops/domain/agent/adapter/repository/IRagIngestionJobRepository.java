package cn.lgs.orbisops.domain.agent.adapter.repository;

import cn.lgs.orbisops.domain.agent.model.entity.RagIngestionJob;

import java.util.List;

public interface IRagIngestionJobRepository {

    void save(RagIngestionJob job);

    RagIngestionJob get(String jobId);

    List<RagIngestionJob> list(int limit);
}
