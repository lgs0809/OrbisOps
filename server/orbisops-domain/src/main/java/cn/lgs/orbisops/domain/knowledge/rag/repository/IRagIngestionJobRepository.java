package cn.lgs.orbisops.domain.knowledge.rag.repository;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJob;

import java.util.List;

public interface IRagIngestionJobRepository {

    void save(RagIngestionJob job);

    RagIngestionJob get(String jobId);

    List<RagIngestionJob> list(int limit);
}
