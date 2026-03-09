package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KnowledgeRagDocumentApplicationService {

    private final KnowledgeRagDocumentPort port;
    private final KnowledgeDocumentCatalogApplicationService documentCatalogService;
    private final KnowledgeProjectExistencePort projectExistencePort;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeRagDocumentApplicationService(
            KnowledgeRagDocumentPort port,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort) {
        this(port, documentCatalogService, projectExistencePort, new KnowledgeCatalogPolicy());
    }

    KnowledgeRagDocumentApplicationService(
            KnowledgeRagDocumentPort port,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort,
            KnowledgeCatalogPolicy policy) {
        if (port == null) throw new IllegalArgumentException("KNOWLEDGE_RAG_DOCUMENT_PORT_REQUIRED");
        if (documentCatalogService == null) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_CATALOG_SERVICE_REQUIRED");
        if (projectExistencePort == null) throw new IllegalArgumentException("KNOWLEDGE_PROJECT_EXISTENCE_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        this.port = port;
        this.documentCatalogService = documentCatalogService;
        this.projectExistencePort = projectExistencePort;
        this.policy = policy;
    }

    public List<Map<String, Object>> list(String scope,
                                          String projectId,
                                          String kbId,
                                          int limit) {
        ScopeContext context = context(scope, projectId);
        String id = policy.requiredId(kbId);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<KnowledgeRagChunk> chunks = safe(port.list(
                id, context.scope().name(), context.projectId())).stream()
                .limit(safeLimit)
                .toList();
        synchronizeCatalogBestEffort(context, id, chunks);
        return chunks.stream().map(this::chunkView).toList();
    }

    public Map<String, Object> statistics(String scope,
                                          String projectId,
                                          String kbId) {
        ScopeContext context = context(scope, projectId);
        String id = value(kbId).isBlank() ? "" : policy.requiredId(kbId);
        KnowledgeDocumentStatistics statistics = port.statistics(
                id, context.scope().name(), context.projectId());
        KnowledgeDocumentStatistics safe = statistics == null
                ? new KnowledgeDocumentStatistics(
                        0L, 0L, List.of(), List.of(), id,
                        context.scope().name(), context.projectId())
                : statistics;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chunkCount", safe.chunkCount());
        result.put("documentCount", safe.documentCount());
        result.put("byType", safe.byType().stream().map(this::breakdownView).toList());
        result.put("bySource", safe.bySource().stream().map(this::breakdownView).toList());
        result.put("tag", safe.knowledgeTag());
        result.put("scope", safe.scope().isBlank() ? context.scope().name() : safe.scope());
        result.put("projectId", safe.projectId().isBlank() ? context.projectId() : safe.projectId());
        return result;
    }

    public boolean deleteChunk(String scope,
                               String projectId,
                               String kbId,
                               String chunkId) {
        ScopeContext context = context(scope, projectId);
        return port.deleteChunk(
                policy.requiredChunk(chunkId),
                policy.requiredId(kbId),
                context.scope().name(),
                context.projectId());
    }

    public boolean deleteChunk(String chunkId) {
        return port.deleteChunk(policy.requiredChunk(chunkId));
    }

    public Map<String, Object> deleteChunks(String scope,
                                            String projectId,
                                            String kbId) {
        ScopeContext context = context(scope, projectId);
        String id = policy.requiredId(kbId);
        KnowledgeChunkDeletionOutcome outcome = port.deleteChunks(
                id, context.scope().name(), context.projectId());
        KnowledgeChunkDeletionOutcome safe = outcome == null
                ? new KnowledgeChunkDeletionOutcome(
                        id, context.scope().name(), context.projectId(), 0L)
                : outcome;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("knowledgeTag", safe.knowledgeTag());
        result.put("scope", safe.scope().isBlank() ? context.scope().name() : safe.scope());
        result.put("projectId", safe.projectId().isBlank() ? context.projectId() : safe.projectId());
        result.put("deletedChunks", safe.deletedChunks());
        return result;
    }

    public Map<String, Object> content(String chunkId) {
        KnowledgeRagChunk chunk = port.content(policy.requiredChunk(chunkId));
        return chunk == null ? Map.of() : chunkView(chunk);
    }

    private ScopeContext context(String scope, String projectId) {
        KnowledgeScope knowledgeScope = KnowledgeScope.require(scope);
        if (knowledgeScope == KnowledgeScope.GLOBAL) {
            return new ScopeContext(knowledgeScope, "");
        }
        String project = policy.requiredProject(projectId);
        if (!projectExistencePort.exists(project)) {
            throw new IllegalArgumentException("项目不存在：" + project);
        }
        return new ScopeContext(knowledgeScope, project);
    }

    private void synchronizeCatalogBestEffort(ScopeContext context,
                                              String kbId,
                                              List<KnowledgeRagChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        try {
            documentCatalogService.synchronizeParsed(
                    context.scope().name(),
                    context.projectId(),
                    kbId,
                    chunks.stream().map(this::parsedChunk).toList());
        } catch (RuntimeException ignored) {
            // The searchable RAG store remains authoritative; catalog synchronization is best effort.
        }
    }

    private KnowledgeParsedChunk parsedChunk(KnowledgeRagChunk chunk) {
        return new KnowledgeParsedChunk(
                chunk.chunkId(),
                chunk.fileName(),
                chunk.displayName(),
                chunk.source(),
                chunk.documentType(),
                chunk.chunkIndex(),
                chunk.chunkStrategy(),
                chunk.size(),
                chunk.content(),
                "READY",
                "VECTOR_PIPELINE",
                chunk.previewable());
    }

    private Map<String, Object> chunkView(KnowledgeRagChunk chunk) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", chunk.fileName());
        result.put("chunkId", text(chunk.chunkId(), chunk.fileName()));
        result.put("kbId", chunk.knowledgeTag());
        result.put("knowledgeTag", chunk.knowledgeTag());
        result.put("tag", chunk.knowledgeTag());
        result.put("fileName", chunk.fileName());
        result.put("displayName", chunk.displayName());
        result.put("source", chunk.source());
        result.put("sourceType", "STRUCTURED_RAG");
        result.put("documentType", chunk.documentType());
        result.put("fileType", chunk.documentType());
        result.put("chunkIndex", chunk.chunkIndex());
        result.put("chunkStrategy", chunk.chunkStrategy());
        result.put("size", chunk.size());
        result.put("content", chunk.content());
        result.put("parseStatus", "READY");
        result.put("embeddingStatus", "VECTOR_PIPELINE");
        result.put("structurePreserved", true);
        result.put("previewable", chunk.previewable());
        result.put("updateTime", chunk.updatedAt());
        return result;
    }

    private Map<String, Object> breakdownView(KnowledgeCountBreakdown breakdown) {
        return Map.of("key", breakdown.key(), "count", breakdown.count());
    }

    private List<KnowledgeRagChunk> safe(List<KnowledgeRagChunk> chunks) {
        return chunks == null ? List.of() : chunks;
    }

    private String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? value(fallback) : normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private record ScopeContext(KnowledgeScope scope, String projectId) {
    }
}
