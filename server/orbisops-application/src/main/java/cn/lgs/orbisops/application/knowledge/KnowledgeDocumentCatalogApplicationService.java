package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeDocumentCatalogRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeDocumentCatalogFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeDocumentCatalogApplicationService {

    private final IKnowledgeDocumentCatalogRepository repository;
    private final KnowledgeCatalogPolicy policy;
    private final KnowledgeDocumentCatalogFactory factory;

    public KnowledgeDocumentCatalogApplicationService(
            IKnowledgeDocumentCatalogRepository repository) {
        this(repository, new KnowledgeCatalogPolicy(), new KnowledgeDocumentCatalogFactory());
    }

    KnowledgeDocumentCatalogApplicationService(
            IKnowledgeDocumentCatalogRepository repository,
            KnowledgeCatalogPolicy policy,
            KnowledgeDocumentCatalogFactory factory) {
        if (repository == null) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_CATALOG_REPOSITORY_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        if (factory == null) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_CATALOG_FACTORY_REQUIRED");
        this.repository = repository;
        this.policy = policy;
        this.factory = factory;
    }

    public void recordSubmitted(String scope,
                                String projectId,
                                String kbId,
                                List<KnowledgeUploadDocument> files,
                                String jobId,
                                String jobStatus) {
        KnowledgeBaseCatalogKey key = key(scope, projectId, kbId);
        if (files == null || files.isEmpty()) {
            return;
        }
        List<KnowledgeDocumentCatalogEntry> documents = files.stream()
                .filter(file -> file != null)
                .map(file -> factory.submitted(
                        key, file.fileName(), file.fileSize(), jobId, jobStatus))
                .toList();
        if (!documents.isEmpty()) {
            repository.saveSubmitted(documents);
        }
    }

    public void synchronizeParsed(String scope,
                                  String projectId,
                                  String kbId,
                                  List<KnowledgeParsedChunk> parsedChunks) {
        KnowledgeBaseCatalogKey key = key(scope, projectId, kbId);
        if (parsedChunks == null || parsedChunks.isEmpty()) {
            return;
        }
        Map<String, List<KnowledgeParsedChunk>> byFile = new LinkedHashMap<>();
        for (KnowledgeParsedChunk chunk : parsedChunks) {
            if (chunk != null) {
                byFile.computeIfAbsent(chunk.fileName(), ignored -> new ArrayList<>()).add(chunk);
            }
        }
        for (Map.Entry<String, List<KnowledgeParsedChunk>> item : byFile.entrySet()) {
            String fileName = item.getKey();
            List<KnowledgeParsedChunk> chunks = item.getValue();
            KnowledgeParsedChunk first = chunks.get(0);
            KnowledgeDocumentCatalogEntry document = factory.parsedDocument(
                    key,
                    fileName,
                    first.displayName(),
                    first.documentType(),
                    first.size(),
                    chunks.size());
            List<KnowledgeChunkCatalogEntry> catalogChunks = chunks.stream()
                    .map(chunk -> factory.parsedChunk(
                            key,
                            document.documentId(),
                            chunk.chunkId(),
                            chunk.chunkIndex(),
                            chunk.chunkStrategy(),
                            chunk.content(),
                            chunk.parseStatus(),
                            chunk.vectorStatus(),
                            chunk.source(),
                            chunk.previewable()))
                    .toList();
            repository.synchronize(document, catalogChunks);
        }
    }

    private KnowledgeBaseCatalogKey key(String scope, String projectId, String kbId) {
        KnowledgeScope knowledgeScope = KnowledgeScope.require(scope);
        String project = knowledgeScope == KnowledgeScope.PROJECT
                ? policy.requiredProject(projectId)
                : "";
        return new KnowledgeBaseCatalogKey(
                knowledgeScope,
                project,
                policy.requiredId(kbId));
    }
}
