package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeDocumentIngestionApplicationService;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.trigger.application.rag.MultipartRagFileResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/** Multipart adapter for the framework-neutral knowledge ingestion application service. */
@Component
public class OpsKnowledgeCatalogAdapter implements
        KnowledgeCatalogPort<RagIngestionJobView, MultipartFile> {

    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_PROJECT = "PROJECT";

    private final KnowledgeDocumentIngestionApplicationService ingestionService;

    public OpsKnowledgeCatalogAdapter(
            KnowledgeDocumentIngestionApplicationService ingestionService) {
        if (ingestionService == null) {
            throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_INGESTION_SERVICE_REQUIRED");
        }
        this.ingestionService = ingestionService;
    }

    @Override
    public RagIngestionJobView importGlobalDocuments(String kbId,
                                                     String name,
                                                     List<MultipartFile> files) {
        return ingestionService.submit(
                SCOPE_GLOBAL, "", name, kbId, resources(files));
    }

    @Override
    public RagIngestionJobView importProjectDocuments(String projectId,
                                                      String kbId,
                                                      String name,
                                                      List<MultipartFile> files) {
        return ingestionService.submit(
                SCOPE_PROJECT, projectId, name, kbId, resources(files));
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
