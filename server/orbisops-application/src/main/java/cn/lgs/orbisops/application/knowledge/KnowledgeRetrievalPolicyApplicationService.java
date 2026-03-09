package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IKnowledgeRetrievalPolicyRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicyState;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.service.KnowledgeCatalogPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KnowledgeRetrievalPolicyApplicationService {

    private final IKnowledgeRetrievalPolicyRepository repository;
    private final KnowledgeBaseCatalogApplicationService catalogService;
    private final KnowledgeCatalogPolicy policy;

    public KnowledgeRetrievalPolicyApplicationService(
            IKnowledgeRetrievalPolicyRepository repository,
            KnowledgeBaseCatalogApplicationService catalogService) {
        this(repository, catalogService, new KnowledgeCatalogPolicy());
    }

    KnowledgeRetrievalPolicyApplicationService(
            IKnowledgeRetrievalPolicyRepository repository,
            KnowledgeBaseCatalogApplicationService catalogService,
            KnowledgeCatalogPolicy policy) {
        if (repository == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_POLICY_REPOSITORY_REQUIRED");
        if (catalogService == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_CATALOG_SERVICE_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("KNOWLEDGE_CATALOG_POLICY_REQUIRED");
        this.repository = repository;
        this.catalogService = catalogService;
        this.policy = policy;
    }

    public Map<String, Object> get(String scope, String projectId, String kbId) {
        return view(resolveState(scope, projectId, kbId));
    }

    public KnowledgeRetrievalPolicy resolve(String scope, String projectId, String kbId) {
        return resolveState(scope, projectId, kbId).policy();
    }

    public Map<String, Object> update(String scope,
                                      String projectId,
                                      String kbId,
                                      KnowledgeRetrievalPolicyCommand command) {
        if (command == null) throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_COMMAND_REQUIRED");
        KnowledgeRetrievalPolicyKey key = key(scope, projectId, kbId);
        KnowledgeRetrievalPolicy retrievalPolicy = policy.retrievalPolicy(
                command.maxSegmentChars(),
                command.hardSplitOverlapChars(),
                command.topK(),
                command.rerankEnabled(),
                command.embeddingModelId(),
                command.metadataFilterJson());
        catalogService.ensureExists(key.scope(), key.projectId(), key.kbId(), command.actor());
        KnowledgeRetrievalPolicyState state = repository.save(key, retrievalPolicy);
        return view(state == null
                ? KnowledgeRetrievalPolicyState.transientState(key, retrievalPolicy)
                : state);
    }

    private KnowledgeRetrievalPolicyState resolveState(String scope, String projectId, String kbId) {
        KnowledgeRetrievalPolicyKey key = key(scope, projectId, kbId);
        return repository.find(key)
                .orElseGet(() -> KnowledgeRetrievalPolicyState.transientState(
                        key, policy.defaultRetrievalPolicy()));
    }

    private KnowledgeRetrievalPolicyKey key(String scope, String projectId, String kbId) {
        KnowledgeScope knowledgeScope = KnowledgeScope.require(scope);
        String project = knowledgeScope == KnowledgeScope.PROJECT
                ? policy.requiredProject(projectId)
                : "";
        return new KnowledgeRetrievalPolicyKey(
                knowledgeScope, project, policy.requiredId(kbId));
    }

    private Map<String, Object> view(KnowledgeRetrievalPolicyState state) {
        KnowledgeRetrievalPolicyKey key = state.key();
        KnowledgeRetrievalPolicy retrievalPolicy = state.policy();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", state.id());
        result.put("kbId", key.kbId());
        result.put("knowledgeTag", key.kbId());
        result.put("scope", key.scope().name());
        result.put("projectId", key.projectId());
        result.put("segmentationMode", "STRUCTURE_FIRST");
        result.put("structurePreserved", true);
        result.putAll(retrievalPolicy.toMap());
        result.put("vectorStatus", "STRUCTURED_RAG");
        result.put("updateTime", state.updatedAt());
        return Collections.unmodifiableMap(result);
    }
}
