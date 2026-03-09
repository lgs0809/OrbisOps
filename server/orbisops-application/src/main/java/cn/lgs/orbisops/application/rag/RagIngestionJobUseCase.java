package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.agent.model.entity.RagFileResource;
import cn.lgs.orbisops.domain.agent.service.rag.RagParsePolicy;

import java.util.List;

public interface RagIngestionJobUseCase {

    RagIngestionJobView submit(String name, String tag, List<RagFileResource> files);

    RagIngestionJobView submit(String name,
                               String tag,
                               List<RagFileResource> files,
                               RagParsePolicy parsePolicy);

    void validate(String name, String tag, List<RagFileResource> files);

    RagIngestionJobView get(String jobId);

    List<RagIngestionJobView> list(int limit);
}
