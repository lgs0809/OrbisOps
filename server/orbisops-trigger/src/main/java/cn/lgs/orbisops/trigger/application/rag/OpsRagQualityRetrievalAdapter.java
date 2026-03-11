package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityProbeCommand;
import cn.lgs.orbisops.application.rag.RagQualityRetrievalHit;
import cn.lgs.orbisops.application.rag.RagQualityRetrievalPort;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagRetrievalSettings;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import cn.lgs.orbisops.trigger.ops.rag.advisor.RagAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trigger adapter for the production RagAnswerAdvisor retrieval chain. */
public final class OpsRagQualityRetrievalAdapter
        implements RagQualityRetrievalPort<Map<String, Object>> {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final IRagKnowledgeRepository ragKnowledgeRepository;
    private final RagMultimodalEmbeddingService multimodalEmbeddingService;
    private final RagQualityEvalSettings settings;

    public OpsRagQualityRetrievalAdapter(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            IRagKnowledgeRepository ragKnowledgeRepository,
            RagMultimodalEmbeddingService multimodalEmbeddingService,
            RagQualityEvalSettings settings) {
        if (vectorStoreProvider == null
                || embeddingModelProvider == null
                || ragKnowledgeRepository == null) {
            throw new IllegalArgumentException("RAG_QUALITY_RETRIEVAL_DEPENDENCIES_REQUIRED");
        }
        this.vectorStoreProvider = vectorStoreProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.ragKnowledgeRepository = ragKnowledgeRepository;
        this.multimodalEmbeddingService = multimodalEmbeddingService;
        this.settings = settings == null ? RagQualityEvalSettings.defaults() : settings;
    }

    @Override
    public List<RagQualityRetrievalHit<Map<String, Object>>> retrieve(
            RagQualityProbeCommand command) {
        Map<String, Object> context = context(command);
        RagRetrievalSettings ragAnswer = answer(command);
        RagAnswerAdvisor advisor = new RagAnswerAdvisor(
                vectorStoreProvider.getIfAvailable(),
                SearchRequest.builder().topK(Math.max(command.topK(), 8)).build(),
                ragAnswer,
                ragKnowledgeRepository,
                multimodalEmbeddingService,
                embeddingModelProvider.getIfAvailable());
        List<Document> documents = advisor.retrieve(command.query(), context);
        return documents.stream().map(this::map).toList();
    }

    private Map<String, Object> context(RagQualityProbeCommand command) {
        Map<String, Object> context = new HashMap<>();
        context.put("qa_retrieval_mode", command.retrievalMode());
        context.put("qa_dynamic_search", true);
        context.put("qa_final_top_k", command.topK());
        context.put("qa_vector_top_k", Math.max(command.topK(), 8));
        context.put("qa_bm25_top_k", Math.max(command.topK(), 10));
        context.put("qa_rerank_enabled", command.rerankEnabled());
        if (!command.knowledgeTag().isBlank()) {
            context.put("qa_filter_expression",
                    "knowledge == '" + escapeFilterValue(command.knowledgeTag()) + "'");
        }
        return context;
    }

    private RagRetrievalSettings answer(RagQualityProbeCommand command) {
        RagRetrievalSettings ragAnswer = new RagRetrievalSettings();
        ragAnswer.setRetrievalMode(command.retrievalMode());
        ragAnswer.setDynamicSearch(true);
        ragAnswer.setVectorTopK(Math.max(command.topK(), 8));
        ragAnswer.setBm25TopK(Math.max(command.topK(), 10));
        ragAnswer.setFinalTopK(command.topK());
        ragAnswer.setRerankEnabled(command.rerankEnabled());
        ragAnswer.setRerankProvider(settings.rerankProvider());
        ragAnswer.setRerankBaseUrl(settings.rerankBaseUrl());
        ragAnswer.setRerankApiKey(settings.rerankApiKey());
        ragAnswer.setRerankPath(settings.rerankPath());
        ragAnswer.setRerankModel(settings.rerankModel());
        ragAnswer.setRerankCandidateTopK(Math.max(command.topK() * 3, 20));
        ragAnswer.setRerankTopN(command.topK());
        return ragAnswer;
    }

    private RagQualityRetrievalHit<Map<String, Object>> map(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null
                ? Map.of()
                : Map.copyOf(document.getMetadata());
        return new RagQualityRetrievalHit<>(
                firstText(metadata, "chunk_id", "id"),
                firstText(metadata, "source", "file_name", "filename", "name"),
                String.valueOf(metadata.getOrDefault("knowledge", "")),
                String.valueOf(metadata.getOrDefault("document_type", "")),
                String.valueOf(metadata.getOrDefault("chunk_strategy", "")),
                document.getText(),
                metadata);
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private String escapeFilterValue(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }
}
