package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagIngestionJobUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Web adapter for the framework-neutral asynchronous RAG ingestion use case. */
@Service
public class RagIngestionJobService {

    private final RagIngestionJobUseCase useCase;

    public RagIngestionJobService(RagIngestionJobUseCase useCase) {
        if (useCase == null) throw new IllegalArgumentException("RAG_INGESTION_JOB_USE_CASE_REQUIRED");
        this.useCase = useCase;
    }

    public RagIngestionJobView submit(String name,
                                      String tag,
                                      List<MultipartFile> files) throws IOException {
        return useCase.submit(name, tag, resources(files));
    }

    public RagIngestionJobView submit(String name,
                                      String tag,
                                      List<MultipartFile> files,
                                      RagParsePolicy parsePolicy) throws IOException {
        return useCase.submit(name, tag, resources(files), parsePolicy);
    }

    public void validate(String name, String tag, List<MultipartFile> files) {
        useCase.validate(name, tag, resources(files));
    }

    public RagIngestionJobView get(String jobId) {
        return useCase.get(jobId);
    }

    public List<RagIngestionJobView> list(int limit) {
        return useCase.list(limit);
    }

    private List<RagFileResource> resources(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<RagFileResource> resources = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            resources.add(file == null ? null : new MultipartRagFileResource(file));
        }
        return resources;
    }
}
