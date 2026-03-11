package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import cn.lgs.orbisops.trigger.ops.rag.advisor.RagAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Unified RAG advisor construction, context, and retrieval protocol boundary. */
public final class OpsRagKnowledgeRetrievalService {

    private final IRagKnowledgeRepository knowledgeRepository;
    private final RetrievalExecutor retrievalExecutor;

    public OpsRagKnowledgeRetrievalService(IRagKnowledgeRepository knowledgeRepository) {
        if (knowledgeRepository == null) {
            throw new IllegalArgumentException("RAG_KNOWLEDGE_REPOSITORY_REQUIRED");
        }
        this.knowledgeRepository = knowledgeRepository;
        this.retrievalExecutor = this::retrieveWithAdvisor;
    }

    boolean bm25Available() {
        return knowledgeRepository != null && knowledgeRepository.available();
    }

    OpsRagKnowledgeRetrievalService(RetrievalExecutor retrievalExecutor) {
        this.knowledgeRepository = null;
        this.retrievalExecutor = retrievalExecutor;
    }

    Result retrieve(Input input, CancellationCheck cancellationCheck) throws Exception {
        cancellationCheck.check();
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(input.settings().rerankEnabled()
                        ? Math.max(5, input.settings().rerankCandidateTopK())
                        : 5)
                .filterExpression(input.knowledgeFilter())
                .build();
        RagRetrievalSettings config = buildConfig(input);
        Map<String, Object> context = buildContext(input);
        List<Document> documents = retrievalExecutor.retrieve(
                input,
                searchRequest,
                config,
                context);
        cancellationCheck.check();
        return new Result(
                documents,
                context.get("qa_retrieval_mode"),
                context.get("qa_rewrite_queries"),
                (String) context.get("qa_llm_query_rewrite_error"),
                (String) context.get("qa_rerank_error"));
    }

    private List<Document> retrieveWithAdvisor(
            Input input,
            SearchRequest searchRequest,
            RagRetrievalSettings config,
            Map<String, Object> context) {
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                input.vectorStore(),
                searchRequest,
                config,
                knowledgeRepository,
                input.multimodalEmbeddingService(),
                input.embeddingModel());
        return advisor.retrieve(input.query(), context);
    }

    private Map<String, Object> buildContext(Input input) {
        Settings settings = input.settings();
        Map<String, Object> context = new HashMap<>();
        context.put("qa_retrieval_mode", input.retrievalMode());
        context.put("qa_filter_expression", input.knowledgeFilter());
        context.put("qa_query_rewrite_mode", settings.queryRewriteMode());
        context.put("qa_llm_query_rewrite_enabled", settings.llmQueryRewriteEnabled());
        context.put("qa_llm_query_rewrite_base_url", settings.llmQueryRewriteBaseUrl());
        context.put("qa_llm_query_rewrite_api_key", settings.llmQueryRewriteApiKey());
        context.put("qa_llm_query_rewrite_path", settings.llmQueryRewritePath());
        context.put("qa_llm_query_rewrite_model", settings.llmQueryRewriteModel());
        context.put("qa_query_rewrite_max_queries", settings.llmQueryRewriteMaxQueries());
        context.put("qa_llm_query_rewrite_timeout_seconds", settings.llmQueryRewriteTimeoutSeconds());
        context.put("qa_llm_query_rewrite_min_chars", settings.llmQueryRewriteMinChars());
        context.put("qa_llm_query_rewrite_on_low_recall", settings.llmQueryRewriteOnLowRecall());
        context.put("qa_llm_query_rewrite_low_recall_min_candidates", settings.llmQueryRewriteLowRecallMinCandidates());
        context.put("qa_fail_on_degradation", settings.failOnLlmDegradation());
        context.put("qa_query_rewrite_fail_on_degradation", false);
        context.put("qa_rerank_fail_on_degradation", false);
        context.put("qa_rerank_enabled", settings.rerankEnabled() && input.rerankAvailable());
        return context;
    }

    private RagRetrievalSettings buildConfig(Input input) {
        Settings settings = input.settings();
        RagRetrievalSettings config = new RagRetrievalSettings();
        config.setRetrievalMode("auto");
        config.setVectorTopK(settings.rerankEnabled()
                ? Math.max(5, Math.min(settings.rerankCandidateTopK(), 20))
                : 5);
        config.setBm25TopK(settings.rerankEnabled()
                ? Math.max(8, Math.min(settings.rerankCandidateTopK(), 30))
                : 8);
        config.setFinalTopK(Math.max(1, Math.min(settings.rerankTopN(), 10)));
        config.setMaxContextChars(12000);
        config.setDynamicSearch(true);
        config.setQueryRewriteMode(settings.queryRewriteMode());
        config.setLlmQueryRewriteEnabled(settings.llmQueryRewriteEnabled());
        config.setLlmQueryRewriteBaseUrl(settings.llmQueryRewriteBaseUrl());
        config.setLlmQueryRewriteApiKey(settings.llmQueryRewriteApiKey());
        config.setLlmQueryRewritePath(settings.llmQueryRewritePath());
        config.setLlmQueryRewriteModel(settings.llmQueryRewriteModel());
        config.setLlmQueryRewriteMaxQueries(settings.llmQueryRewriteMaxQueries());
        config.setLlmQueryRewriteTimeoutSeconds(settings.llmQueryRewriteTimeoutSeconds());
        config.setLlmQueryRewriteMinChars(settings.llmQueryRewriteMinChars());
        config.setLlmQueryRewriteOnLowRecall(settings.llmQueryRewriteOnLowRecall());
        config.setLlmQueryRewriteLowRecallMinCandidates(settings.llmQueryRewriteLowRecallMinCandidates());
        config.setRerankEnabled(settings.rerankEnabled() && input.rerankAvailable());
        config.setRerankProvider(settings.rerankProvider());
        config.setRerankBaseUrl(settings.rerankBaseUrl());
        config.setRerankApiKey(settings.rerankApiKey());
        config.setRerankPath(settings.rerankPath());
        config.setRerankModel(settings.rerankModel());
        config.setRerankCandidateTopK(settings.rerankCandidateTopK());
        config.setRerankTopN(settings.rerankTopN());
        config.setRerankMaxDocChars(settings.rerankMaxDocChars());
        config.setFilterExpression(input.knowledgeFilter());
        return config;
    }

    record Input(
            String query,
            String retrievalMode,
            String knowledgeFilter,
            boolean rerankAvailable,
            VectorStore vectorStore,
            RagMultimodalEmbeddingService multimodalEmbeddingService,
            EmbeddingModel embeddingModel,
            Settings settings) {
    }

    record Settings(
            boolean rerankEnabled,
            String rerankProvider,
            String rerankBaseUrl,
            String rerankApiKey,
            String rerankPath,
            String rerankModel,
            int rerankCandidateTopK,
            int rerankTopN,
            int rerankMaxDocChars,
            String queryRewriteMode,
            boolean llmQueryRewriteEnabled,
            String llmQueryRewriteBaseUrl,
            String llmQueryRewriteApiKey,
            String llmQueryRewritePath,
            String llmQueryRewriteModel,
            int llmQueryRewriteMaxQueries,
            int llmQueryRewriteTimeoutSeconds,
            int llmQueryRewriteMinChars,
            boolean llmQueryRewriteOnLowRecall,
            int llmQueryRewriteLowRecallMinCandidates,
            boolean failOnLlmDegradation) {
    }

    record Result(
            List<Document> documents,
            Object retrievalMode,
            Object rewriteQueries,
            String queryRewriteError,
            String rerankError) {
    }

    @FunctionalInterface
    interface RetrievalExecutor {
        List<Document> retrieve(
                Input input,
                SearchRequest searchRequest,
                RagRetrievalSettings config,
                Map<String, Object> context);
    }

    @FunctionalInterface
    interface CancellationCheck {
        void check();
    }
}
