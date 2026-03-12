package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRecallCoordinatorBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRecallCoordinator.java";
    private static final String RANKED_DOCUMENT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRankedDocument.java";

    @Test
    void recallFanOutAndVectorFallbackMustRemainInsideDedicatedCoordinator() throws IOException {
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(coordinator.contains("class RagRecallCoordinator")),
                () -> assertTrue(coordinator.contains("RepositoryVectorRecall")),
                () -> assertTrue(coordinator.contains("SpringVectorRecall")),
                () -> assertTrue(coordinator.contains("MultimodalRecall")),
                () -> assertTrue(coordinator.contains("LexicalRecall")),
                () -> assertTrue(coordinator.contains("searchVectorCandidates")),
                () -> assertTrue(coordinator.contains("similaritySearch")),
                () -> assertTrue(coordinator.contains("FilterExpressionTextParser")),
                () -> assertTrue(coordinator.contains("vectorLiteral")),
                () -> assertTrue(coordinator.contains("qa_fail_on_degradation")),
                () -> assertTrue(coordinator.contains("qa_exclude_memory_documents")),
                () -> assertTrue(coordinator.contains("\"vector\"")),
                () -> assertTrue(coordinator.contains("\"multimodal\"")),
                () -> assertTrue(coordinator.contains("\"bm25\"")),
                () -> assertFalse(coordinator.contains("bm25Score")),
                () -> assertFalse(coordinator.contains("TOKEN_PATTERN")),
                () -> assertFalse(coordinator.contains("searchLexicalCandidates")),
                () -> assertFalse(coordinator.contains("rerank_score")),
                () -> assertFalse(coordinator.contains("HttpClient")),
                () -> assertFalse(coordinator.contains("ChatClientRequest")));
    }

    @Test
    void advisorMustDelegateRecallWithoutReimplementingSourceFanOut() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRecallCoordinator")),
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("recallCoordinator.recall")),
                () -> assertFalse(advisor.contains("this::bm25Search")),
                () -> assertFalse(advisor.contains("bm25Search")),
                () -> assertFalse(advisor.contains("private List<RagRankedDocument> recallDocuments")),
                () -> assertFalse(advisor.contains("private List<Document> vectorRecall")),
                () -> assertFalse(advisor.contains("addRankedDocuments")),
                () -> assertFalse(advisor.contains("vectorLiteral")),
                () -> assertFalse(advisor.contains("private final VectorStore vectorStore")),
                () -> assertFalse(advisor.contains("private final SearchRequest searchRequest")),
                () -> assertFalse(advisor.contains("private final EmbeddingModel embeddingModel")),
                () -> assertFalse(advisor.contains("private final RagMultimodalEmbeddingService ragMultimodalEmbeddingService")),
                () -> assertFalse(advisor.contains("private record RankedDocument")),
                () -> assertFalse(advisor.contains("isOpsChatMemory(Document document)")),
                () -> assertTrue(advisor.lines().count() < 760));
    }

    @Test
    void rankedRecallResultMustRemainTypedAndOwnStableFusionKey() throws IOException {
        String rankedDocument = read(RANKED_DOCUMENT);

        assertAll(
                () -> assertTrue(rankedDocument.contains("public record RagRankedDocument")),
                () -> assertTrue(rankedDocument.contains("Document document")),
                () -> assertTrue(rankedDocument.contains("String source")),
                () -> assertTrue(rankedDocument.contains("int rank")),
                () -> assertTrue(rankedDocument.contains("double score")),
                () -> assertTrue(rankedDocument.contains("public String key()")),
                () -> assertTrue(rankedDocument.contains("chunk_index")),
                () -> assertFalse(rankedDocument.contains("IRagKnowledgeRepository")),
                () -> assertFalse(rankedDocument.contains("VectorStore")),
                () -> assertFalse(rankedDocument.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(rankedDocument.contains("HttpClient")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
