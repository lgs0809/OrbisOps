package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMmrDiversityBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String SELECTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagMmrDiversitySelector.java";

    @Test
    void mmrConfigurationRelevanceJaccardAndGreedyProjectionMustRemainInsideSelector() throws IOException {
        String selector = read(SELECTOR);

        assertAll(
                () -> assertTrue(selector.contains("class RagMmrDiversitySelector")),
                () -> assertTrue(selector.contains("qa_mmr_enabled")),
                () -> assertTrue(selector.contains("qa_mmr_lambda")),
                () -> assertTrue(selector.contains("DEFAULT_LAMBDA = 0.72d")),
                () -> assertTrue(selector.contains("MIN_LAMBDA = 0.1d")),
                () -> assertTrue(selector.contains("MAX_LAMBDA = 0.95d")),
                () -> assertTrue(selector.contains("\"rerank_score\"")),
                () -> assertTrue(selector.contains("\"retrieval_score\"")),
                () -> assertTrue(selector.contains("\"bm25_score\"")),
                () -> assertTrue(selector.contains("lexicalSimilarity")),
                () -> assertTrue(selector.contains("intersection.retainAll")),
                () -> assertTrue(selector.contains("union.addAll")),
                () -> assertTrue(selector.contains("lambda * relevance - (1 - lambda) * diversityPenalty")),
                () -> assertTrue(selector.contains("if (score > bestScore)")),
                () -> assertTrue(selector.contains("mmr_selected")),
                () -> assertTrue(selector.contains("mmr_score")),
                () -> assertTrue(selector.contains("selected.addAll(remaining)")),
                () -> assertFalse(selector.contains("IRagKnowledgeRepository")),
                () -> assertFalse(selector.contains("VectorStore")),
                () -> assertFalse(selector.contains("RagRecallCoordinator")),
                () -> assertFalse(selector.contains("RagReciprocalRankFusion")),
                () -> assertFalse(selector.contains("HttpClient")),
                () -> assertFalse(selector.contains("com.alibaba.fastjson")),
                () -> assertFalse(selector.contains("ChatClientRequest")));
    }

    @Test
    void advisorMustDelegateMmrWithoutOwningItsConfigurationOrAlgorithm() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("RagMmrDiversitySelector mmrDiversitySelector")),
                () -> assertFalse(advisor.contains("mmrDiversitySelector.select")),
                () -> assertFalse(advisor.contains("applyMmrDiversity")),
                () -> assertFalse(advisor.contains("qa_mmr_enabled")),
                () -> assertFalse(advisor.contains("qa_mmr_lambda")),
                () -> assertFalse(advisor.contains("mmr_selected")),
                () -> assertFalse(advisor.contains("mmr_score")),
                () -> assertFalse(advisor.contains("relevanceScore")),
                () -> assertFalse(advisor.contains("lexicalSimilarity")),
                () -> assertFalse(advisor.contains("RagLexicalTextPolicy lexicalTextPolicy")),
                () -> assertFalse(advisor.contains("doubleFromContext")),
                () -> assertFalse(advisor.contains("rerankProtocol.rerank")),
                () -> assertTrue(advisor.lines().count() < 460));
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
