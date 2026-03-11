package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import cn.lgs.orbisops.trigger.ops.rag.advisor.RagAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Resolves optional RAG infrastructure and creates the retrieval advisor. */
public final class OpsNodeRagAdvisorFactory {

    private final Supplier<VectorStore> vectorStoreSupplier;
    private final Supplier<RagMultimodalEmbeddingService> multimodalSupplier;
    private final Supplier<EmbeddingModel> embeddingModelSupplier;
    private final ModelAvailabilityPort aiModelAvailability;
    private final IRagKnowledgeRepository ragKnowledgeRepository;

    public OpsNodeRagAdvisorFactory(
            Supplier<VectorStore> vectorStoreSupplier,
            Supplier<RagMultimodalEmbeddingService> multimodalSupplier,
            Supplier<EmbeddingModel> embeddingModelSupplier,
            ModelAvailabilityPort aiModelAvailability,
            IRagKnowledgeRepository ragKnowledgeRepository) {
        this.vectorStoreSupplier = required(
                vectorStoreSupplier, "VECTOR_STORE_SUPPLIER_REQUIRED");
        this.multimodalSupplier = required(
                multimodalSupplier, "MULTIMODAL_EMBEDDING_SUPPLIER_REQUIRED");
        this.embeddingModelSupplier = required(
                embeddingModelSupplier, "EMBEDDING_MODEL_SUPPLIER_REQUIRED");
        if (aiModelAvailability == null) {
            throw new IllegalArgumentException("AI_MODEL_AVAILABILITY_REQUIRED");
        }
        if (ragKnowledgeRepository == null) {
            throw new IllegalArgumentException("RAG_KNOWLEDGE_REPOSITORY_REQUIRED");
        }
        this.aiModelAvailability = aiModelAvailability;
        this.ragKnowledgeRepository = ragKnowledgeRepository;
    }

    public Resources resolve(OpsNodeRagSettings settings) {
        VectorStore vectorStore = vectorStoreSupplier.get();
        boolean embeddingAvailable = aiModelAvailability.isEmbeddingAvailable();
        return new Resources(
                vectorStore,
                multimodalSupplier.get(),
                embeddingModelSupplier.get(),
                embeddingAvailable,
                embeddingAvailable && vectorStore != null,
                ragKnowledgeRepository.available(),
                aiModelAvailability.isRerankAvailable(settings.rerank().apiKey()),
                aiModelAvailability.isChatAvailable());
    }

    public List<Document> retrieve(
            Resources resources,
            int finalTopK,
            OpsNodeRagSettings settings,
            String retrievalQuery,
            Map<String, Object> context) {
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                resources.vectorStore(),
                SearchRequest.builder().topK(finalTopK).build(),
                advisorSettings(settings),
                ragKnowledgeRepository,
                resources.multimodalEmbeddingService(),
                resources.embeddingModel());
        return advisor.retrieve(retrievalQuery, context);
    }

    public String embeddingUnavailableMessage() {
        return aiModelAvailability.unavailableMessage("节点级 RAG 向量检索");
    }

    private RagRetrievalSettings advisorSettings(
            OpsNodeRagSettings settings) {
        OpsNodeRagSettings.QueryRewrite rewrite = settings.queryRewrite();
        OpsNodeRagSettings.Rerank rerank = settings.rerank();
        RagRetrievalSettings answer = new RagRetrievalSettings();
        answer.setRetrievalMode("auto");
        answer.setDynamicSearch(true);
        answer.setVectorTopK(8);
        answer.setBm25TopK(10);
        answer.setFinalTopK(6);
        answer.setQueryRewriteMode(rewrite.mode());
        answer.setLlmQueryRewriteEnabled(rewrite.llmEnabled() && aiModelAvailability.isChatAvailable());
        answer.setLlmQueryRewriteBaseUrl(rewrite.baseUrl());
        answer.setLlmQueryRewriteApiKey(rewrite.apiKey());
        answer.setLlmQueryRewritePath(rewrite.path());
        answer.setLlmQueryRewriteModel(rewrite.model());
        answer.setLlmQueryRewriteMaxQueries(rewrite.maxQueries());
        answer.setLlmQueryRewriteTimeoutSeconds(rewrite.timeoutSeconds());
        answer.setLlmQueryRewriteMinChars(rewrite.minChars());
        answer.setLlmQueryRewriteOnLowRecall(rewrite.onLowRecall());
        answer.setLlmQueryRewriteLowRecallMinCandidates(rewrite.lowRecallMinCandidates());
        answer.setRerankEnabled(rerank.enabled()
                && aiModelAvailability.isRerankAvailable(rerank.apiKey()));
        answer.setRerankProvider(rerank.provider());
        answer.setRerankBaseUrl(rerank.baseUrl());
        answer.setRerankApiKey(rerank.apiKey());
        answer.setRerankPath(rerank.path());
        answer.setRerankModel(rerank.model());
        answer.setRerankCandidateTopK(rerank.candidateTopK());
        answer.setRerankTopN(rerank.topN());
        answer.setRerankMaxDocChars(rerank.maxDocumentChars());
        return answer;
    }

    private <T> Supplier<T> required(Supplier<T> supplier, String code) {
        if (supplier == null) {
            throw new IllegalArgumentException(code);
        }
        return supplier;
    }

    public record Resources(
            VectorStore vectorStore,
            RagMultimodalEmbeddingService multimodalEmbeddingService,
            EmbeddingModel embeddingModel,
            boolean embeddingAvailable,
            boolean vectorAvailable,
            boolean bm25Available,
            boolean rerankAvailable,
            boolean chatAvailable) {
    }
}
