package cn.lgs.orbisops.trigger.ops.rag.advisor;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagLexicalChunkRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Repository-backed BM25 lexical recall adapter.
 */
@Slf4j
public final class RagBm25LexicalRecall implements RagRecallCoordinator.LexicalRecall {

    private static final int MAX_CANDIDATE_TERMS = 32;
    private static final double K1 = 1.5d;
    private static final double B = 0.75d;

    private final IRagKnowledgeRepository repository;
    private final RagLexicalTextPolicy lexicalTextPolicy;

    public RagBm25LexicalRecall(IRagKnowledgeRepository repository) {
        this(repository, new RagLexicalTextPolicy());
    }

    RagBm25LexicalRecall(IRagKnowledgeRepository repository,
                         RagLexicalTextPolicy lexicalTextPolicy) {
        this.repository = repository;
        this.lexicalTextPolicy = lexicalTextPolicy;
    }

    @Override
    public List<Document> search(String query, String filterExpression, int topK) {
        List<String> queryTerms = lexicalTextPolicy.tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        List<Bm25Chunk> chunks = loadChunks(
                filterExpression,
                queryTerms,
                Math.max(100, topK * 50)).stream()
                .filter(chunk -> !isOpsChatMemory(chunk.metadata()))
                .toList();
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> docFrequency = documentFrequency(chunks);
        double averageLength = chunks.stream().mapToInt(Bm25Chunk::length).average().orElse(1.0d);
        int documentCount = chunks.size();

        return chunks.stream()
                .map(chunk -> new Bm25ScoredChunk(
                        chunk,
                        score(queryTerms, chunk, docFrequency, documentCount, averageLength)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(Bm25ScoredChunk::score).reversed())
                .limit(topK)
                .map(scored -> toDocument(scored.chunk(), scored.score()))
                .collect(Collectors.toList());
    }

    private List<Bm25Chunk> loadChunks(String filterExpression,
                                       List<String> queryTerms,
                                       int candidateLimit) {
        if (repository == null || !repository.available()) {
            log.warn("RAG 词法检索仓储未配置，BM25 检索跳过。");
            return Collections.emptyList();
        }

        List<String> candidateTerms = lexicalTextPolicy.candidateTerms(queryTerms, MAX_CANDIDATE_TERMS);
        if (candidateTerms.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return repository.searchLexicalCandidates(filterExpression, candidateTerms, candidateLimit)
                    .stream()
                    .map(this::toChunk)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("RAG BM25 候选召回失败，将降级为空结果：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Bm25Chunk toChunk(RagLexicalChunkRecord record) {
        List<String> terms = lexicalTextPolicy.tokenize(record.text());
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : terms) {
            termFrequency.merge(term, 1, Integer::sum);
        }
        Map<String, Object> metadata = new HashMap<>(record.metadata() == null ? Map.of() : record.metadata());
        metadata.put("chunk_id", record.id());
        metadata.put("retrieval_source", "bm25");
        return new Bm25Chunk(record.id(), record.text(), metadata, termFrequency, terms.size());
    }

    private Map<String, Integer> documentFrequency(List<Bm25Chunk> chunks) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Bm25Chunk chunk : chunks) {
            for (String term : chunk.termFrequency().keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        return documentFrequency;
    }

    private double score(List<String> queryTerms,
                         Bm25Chunk chunk,
                         Map<String, Integer> documentFrequency,
                         int documentCount,
                         double averageLength) {
        double score = 0d;
        for (String term : queryTerms) {
            int termFrequency = chunk.termFrequency().getOrDefault(term, 0);
            if (termFrequency == 0) {
                continue;
            }
            int frequency = documentFrequency.getOrDefault(term, 0);
            double inverseDocumentFrequency = Math.log(
                    1 + (documentCount - frequency + 0.5d) / (frequency + 0.5d));
            double denominator = termFrequency
                    + K1 * (1 - B + B * chunk.length() / averageLength);
            score += inverseDocumentFrequency
                    * (termFrequency * (K1 + 1))
                    / denominator;
        }
        return score;
    }

    private Document toDocument(Bm25Chunk chunk, double score) {
        Map<String, Object> metadata = new HashMap<>(chunk.metadata());
        metadata.put("bm25_score", score);
        return new Document(chunk.id(), chunk.text(), metadata);
    }

    private boolean isOpsChatMemory(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        return "ops_chat".equals(String.valueOf(metadata.get("memory_type")))
                || "ops-chat-memory".equals(String.valueOf(metadata.get("knowledge")));
    }

    private record Bm25Chunk(String id,
                             String text,
                             Map<String, Object> metadata,
                             Map<String, Integer> termFrequency,
                             int length) {
    }

    private record Bm25ScoredChunk(Bm25Chunk chunk, double score) {
    }
}
