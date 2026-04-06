package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticVectorRecallPort;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Spring AI VectorStore adapter for semantic-memory recall. */
@Component
public class OpsSemanticVectorRecallAdapter implements SemanticVectorRecallPort {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final SemanticMemoryPolicy semanticPolicy = new SemanticMemoryPolicy(
            new MemoryContentHashPolicy());

    public OpsSemanticVectorRecallAdapter(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public List<SemanticMemoryRankedCandidate> recallVector(String sessionId,
                                                            String userId,
                                                            String query,
                                                            int recallLimit) {
        VectorStore vectorStore = vectorStoreProvider == null
                ? null
                : vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore 未初始化");
        }
        var requestBuilder = SearchRequest.builder()
                .query(query)
                .topK(recallLimit);
        Filter.Expression filterExpression = memoryFilterExpression(sessionId);
        if (filterExpression != null) {
            requestBuilder.filterExpression(filterExpression);
        }
        List<Document> rawDocuments = vectorStore.similaritySearch(requestBuilder.build()).stream().toList();
        List<SemanticMemoryRankedCandidate> rankedDocuments = new ArrayList<>();
        for (int i = 0; i < rawDocuments.size(); i++) {
            Document document = rawDocuments.get(i);
            if (!inScope(document, sessionId, userId)) {
                continue;
            }
            int rank = i + 1;
            Map<String, Object> metadata = new HashMap<>(document.getMetadata());
            metadata.put("memory_vector_rank", rank);
            rankedDocuments.add(new SemanticMemoryRankedCandidate(
                    snapshot(document, metadata),
                    "vector",
                    rank,
                    semanticPolicy.rrfScore(rank)));
        }
        return rankedDocuments;
    }

    private Filter.Expression memoryFilterExpression(String sessionId) {
        try {
            return new FilterExpressionTextParser().parse(
                    "memory_type == 'ops_chat' && memory_kind == 'message' && session_id == '"
                            + escapeFilterValue(sessionId) + "'");
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean inScope(Document document, String sessionId, String userId) {
        return document != null
                && semanticPolicy.inScope(document.getMetadata(), sessionId, userId);
    }

    private SemanticMemoryDocumentSnapshot snapshot(Document document, Map<String, Object> metadata) {
        return new SemanticMemoryDocumentSnapshot(
                document.getId(),
                document.getText(),
                metadata);
    }

    private String escapeFilterValue(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }
}
