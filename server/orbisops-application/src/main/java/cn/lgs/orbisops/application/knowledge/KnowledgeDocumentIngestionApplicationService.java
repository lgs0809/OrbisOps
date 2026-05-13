package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.application.rag.RagIngestionJobUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Application coordinator for scoped structured knowledge ingestion. */
public final class KnowledgeDocumentIngestionApplicationService {

    private static final System.Logger LOG = System.getLogger(
            KnowledgeDocumentIngestionApplicationService.class.getName());
    private static final String STRUCTURE_NOTE =
            "先按 Markdown 标题、代码块、图片引用和 PDF 页/段落/图注保留结构；只有单个结构块超长时才按长度二次分段";

    private final RagIngestionJobUseCase ingestionJobUseCase;
    private final KnowledgeRetrievalPolicyApplicationService retrievalPolicyService;
    private final KnowledgeDocumentCatalogApplicationService documentCatalogService;
    private final KnowledgeProjectExistencePort projectExistencePort;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeDocumentIngestionApplicationService(
            RagIngestionJobUseCase ingestionJobUseCase,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort) {
        this(ingestionJobUseCase, retrievalPolicyService, documentCatalogService,
                projectExistencePort, new KnowledgeCatalogPolicy());
    }

    KnowledgeDocumentIngestionApplicationService(
            RagIngestionJobUseCase ingestionJobUseCase,
            KnowledgeRetrievalPolicyApplicationService retrievalPolicyService,
            KnowledgeDocumentCatalogApplicationService documentCatalogService,
            KnowledgeProjectExistencePort projectExistencePort,
            KnowledgeCatalogPolicy policy) {
        if (ingestionJobUseCase == null) {
            throw new IllegalArgumentException("RAG_INGESTION_JOB_USE_CASE_REQUIRED");
        }
        if (retrievalPolicyService == null) {
            throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_SERVICE_REQUIRED");
        }
        if (documentCatalogService == null) {
            throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_CATALOG_SERVICE_REQUIRED");
        }
        if (projectExistencePort == null) {
            throw new IllegalArgumentException("KNOWLEDGE_PROJECT_EXISTENCE_PORT_REQUIRED");
        }
        if (policy == null) {
            throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        }
        this.ingestionJobUseCase = ingestionJobUseCase;
        this.retrievalPolicyService = retrievalPolicyService;
        this.documentCatalogService = documentCatalogService;
        this.projectExistencePort = projectExistencePort;
        this.policy = policy;
    }

    public RagIngestionJobView submit(String scope,
                                      String projectId,
                                      String name,
                                      String kbId,
                                      List<RagFileResource> files) {
        try {
            ScopeContext context = context(scope, projectId);
            String id = policy.requiredId(kbId);
            String jobName = text(name, id);
            List<RagFileResource> resources = files == null
                    ? List.of()
                    : new ArrayList<>(files);
            KnowledgeRetrievalPolicy retrievalPolicy = retrievalPolicyService.resolve(
                    context.scope().name(), context.projectId(), id);
            RagParsePolicy parsePolicy = new RagParsePolicy(
                    context.scope().name(),
                    context.projectId(),
                    retrievalPolicy.maxSegmentChars(),
                    retrievalPolicy.hardSplitOverlapChars());
            RagIngestionJobView job = ingestionJobUseCase.submit(
                    jobName, id, resources, parsePolicy);
            recordSubmittedBestEffort(context, id, resources, job);
            return enriched(job, parsePolicy);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "提交结构化 RAG 入库任务失败：" + reason(error), error);
        }
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

    private void recordSubmittedBestEffort(ScopeContext context,
                                           String kbId,
                                           List<RagFileResource> files,
                                           RagIngestionJobView job) {
        if (files.isEmpty()) {
            return;
        }
        try {
            List<KnowledgeUploadDocument> documents = files.stream()
                    .filter(file -> file != null)
                    .map(file -> new KnowledgeUploadDocument(file.fileName(), file.size()))
                    .toList();
            documentCatalogService.recordSubmitted(
                    context.scope().name(),
                    context.projectId(),
                    kbId,
                    documents,
                    job == null ? "" : job.jobId(),
                    job == null ? "" : job.status());
        } catch (RuntimeException error) {
            LOG.log(System.Logger.Level.WARNING,
                    "Knowledge ingestion catalog synchronization failed for " + kbId,
                    error);
        }
    }

    private RagIngestionJobView enriched(RagIngestionJobView job, RagParsePolicy parsePolicy) {
        if (job == null) {
            throw new IllegalStateException("RAG_INGESTION_JOB_RESULT_REQUIRED");
        }
        return new RagIngestionJobView(
                job.jobId(),
                job.status(),
                job.name(),
                job.tag(),
                job.fileNames(),
                job.totalBytes(),
                job.errorMessage(),
                job.createdAt(),
                job.updatedAt(),
                Map.of(
                        "ingestionPipeline", "RagDocumentParser",
                        "segmentationMode", "STRUCTURE_FIRST",
                        "structurePreserved", true,
                        "scope", parsePolicy.scope(),
                        "projectId", parsePolicy.projectId(),
                        "maxSegmentChars", parsePolicy.maxSegmentChars(),
                        "hardSplitOverlapChars", parsePolicy.hardSplitOverlapChars(),
                        "vectorStatus", "ASYNC",
                        "note", STRUCTURE_NOTE));
    }

    private String text(String input, String fallback) {
        String normalized = input == null ? "" : input.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String reason(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private record ScopeContext(KnowledgeScope scope, String projectId) {
    }
}
