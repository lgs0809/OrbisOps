package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates vector, multimodal and lexical recall fan-out before fusion.
 */
@Slf4j
public final class RagRecallCoordinator {

    private final RepositoryVectorRecall repositoryVectorRecall;
    private final SpringVectorRecall springVectorRecall;
    private final MultimodalRecall multimodalRecall;
    private final LexicalRecall lexicalRecall;

    public RagRecallCoordinator(VectorStore vectorStore,
                                SearchRequest searchRequest,
                                IRagKnowledgeRepository ragKnowledgeRepository,
                                RagMultimodalEmbeddingService multimodalEmbeddingService,
                                EmbeddingModel embeddingModel) {
        this(repositoryVectorRecall(embeddingModel, ragKnowledgeRepository),
                springVectorRecall(vectorStore, searchRequest),
                multimodalRecall(multimodalEmbeddingService),
                new RagBm25LexicalRecall(ragKnowledgeRepository));
    }

    RagRecallCoordinator(RepositoryVectorRecall repositoryVectorRecall,
                         SpringVectorRecall springVectorRecall,
                         MultimodalRecall multimodalRecall,
                         LexicalRecall lexicalRecall) {
        this.repositoryVectorRecall = repositoryVectorRecall;
        this.springVectorRecall = springVectorRecall;
        this.multimodalRecall = multimodalRecall;
        this.lexicalRecall = lexicalRecall;
    }

    public List<RagRankedDocument> recall(String userText,
                                          Map<String, Object> context,
                                          RagRetrievalPlan plan,
                                          String filterExpression,
                                          List<String> retrievalQueries) {
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        List<RagRankedDocument> rankedDocuments = new ArrayList<>();
        List<String> safeQueries = retrievalQueries == null || retrievalQueries.isEmpty()
                ? List.of(userText)
                : retrievalQueries;

        if (!"bm25".equals(plan.mode())) {
            for (String retrievalQuery : safeQueries) {
                addRankedDocuments(
                        rankedDocuments,
                        vectorRecall(retrievalQuery, safeContext, filterExpression, plan.vectorTopK()),
                        "vector");
            }
        }

        if (!"bm25".equals(plan.mode())
                && multimodalRecall != null
                && multimodalRecall.available()) {
            addRankedDocuments(
                    rankedDocuments,
                    multimodalRecall.search(userText, filterExpression, plan.vectorTopK()),
                    "multimodal");
        }

        if (!"vector".equals(plan.mode()) && lexicalRecall != null) {
            addRankedDocuments(
                    rankedDocuments,
                    lexicalRecall.search(String.join(" ", safeQueries), filterExpression, plan.bm25TopK()),
                    "bm25");
        }

        if (booleanFromContext(safeContext, "qa_exclude_memory_documents", true)) {
            return rankedDocuments.stream()
                    .filter(rankedDocument -> !isOpsChatMemory(rankedDocument.document()))
                    .toList();
        }
        return rankedDocuments;
    }

    private List<Document> vectorRecall(String retrievalQuery,
                                        Map<String, Object> context,
                                        String filterExpression,
                                        int topK) {
        if (repositoryVectorRecall != null && repositoryVectorRecall.available()) {
            try {
                return repositoryVectorRecall.search(retrievalQuery, filterExpression, topK);
            } catch (Exception e) {
                rejectIfStrict(context, "RAG vector halfvec/HNSW 检索失败：" + e.getMessage(), e);
                log.warn("RAG vector halfvec/HNSW 检索失败，回退 Spring AI VectorStore：{}", e.getMessage());
            }
        }
        if (springVectorRecall == null) {
            return List.of();
        }
        return springVectorRecall.search(retrievalQuery, context, topK);
    }

    private void addRankedDocuments(List<RagRankedDocument> target,
                                    List<Document> documents,
                                    String source) {
        if (documents == null) {
            return;
        }
        for (int index = 0; index < documents.size(); index++) {
            double score = 1.0d / (60 + index + 1);
            target.add(new RagRankedDocument(documents.get(index), source, index + 1, score));
        }
    }

    private void rejectIfStrict(Map<String, Object> context, String message, Exception cause) {
        if (booleanFromContext(context, "qa_fail_on_degradation", false)) {
            throw cause == null
                    ? new IllegalStateException(message)
                    : new IllegalStateException(message, cause);
        }
    }

    private boolean isOpsChatMemory(Document document) {
        if (document == null) {
            return false;
        }
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        return "ops_chat".equals(String.valueOf(metadata.get("memory_type")))
                || "ops-chat-memory".equals(String.valueOf(metadata.get("knowledge")));
    }

    private boolean booleanFromContext(Map<String, Object> context, String key, boolean defaultValue) {
        Object value = context.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private static RepositoryVectorRecall repositoryVectorRecall(EmbeddingModel embeddingModel,
                                                                  IRagKnowledgeRepository repository) {
        return new RepositoryVectorRecall() {
            @Override
            public boolean available() {
                return embeddingModel != null && repository != null && repository.available();
            }

            @Override
            public List<Document> search(String query, String filterExpression, int topK) {
                float[] embedding = embeddingModel.embed(query);
                return repository.searchVectorCandidates(
                                filterExpression,
                                vectorLiteral(embedding),
                                embedding.length,
                                topK)
                        .stream()
                        .map(RagRecallCoordinator::springDocument)
                        .toList();
            }
        };
    }

    private static SpringVectorRecall springVectorRecall(VectorStore vectorStore,
                                                          SearchRequest searchRequest) {
        if (vectorStore == null) {
            return null;
        }
        return (query, context, topK) -> {
            SearchRequest vectorRequest = SearchRequest.from(searchRequest)
                    .query(query)
                    .topK(topK)
                    .filterExpression(filterExpression(context, searchRequest))
                    .build();
            return vectorStore.similaritySearch(vectorRequest);
        };
    }

    private static MultimodalRecall multimodalRecall(RagMultimodalEmbeddingService service) {
        if (service == null) {
            return null;
        }
        return new MultimodalRecall() {
            @Override
            public boolean available() {
                return service.isSearchAvailable();
            }

            @Override
            public List<Document> search(String query, String filterExpression, int topK) {
                return service.search(query, filterExpression, topK);
            }
        };
    }

    private static Filter.Expression filterExpression(Map<String, Object> context,
                                                       SearchRequest searchRequest) {
        if (context.containsKey("qa_filter_expression")
                && StringUtils.hasText(context.get("qa_filter_expression").toString())) {
            return new FilterExpressionTextParser().parse(context.get("qa_filter_expression").toString());
        }
        return searchRequest.getFilterExpression();
    }

    private static Document springDocument(RagDocument document) {
        return new Document(document.id(), document.text(), document.metadata());
    }

    private static String vectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalStateException("Embedding response is empty");
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.10f", embedding[i]));
        }
        return builder.append(']').toString();
    }

    interface RepositoryVectorRecall {
        boolean available();

        List<Document> search(String query, String filterExpression, int topK) throws Exception;
    }

    @FunctionalInterface
    interface SpringVectorRecall {
        List<Document> search(String query, Map<String, Object> context, int topK);
    }

    interface MultimodalRecall {
        boolean available();

        List<Document> search(String query, String filterExpression, int topK);
    }

    @FunctionalInterface
    public interface LexicalRecall {
        List<Document> search(String query, String filterExpression, int topK);
    }
}
