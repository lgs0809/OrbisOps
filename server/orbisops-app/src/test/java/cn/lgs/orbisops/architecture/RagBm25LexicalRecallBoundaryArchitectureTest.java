package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagBm25LexicalRecallBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRecallCoordinator.java";
    private static final String TEXT_POLICY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagLexicalTextPolicy.java";
    private static final String BM25_RECALL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagBm25LexicalRecall.java";

    @Test
    void lexicalTextRulesMustRemainFrameworkNeutralAndCentralized() throws IOException {
        String policy = read(TEXT_POLICY);

        assertAll(
                () -> assertTrue(policy.contains("class RagLexicalTextPolicy")),
                () -> assertTrue(policy.contains("TOKEN_PATTERN")),
                () -> assertTrue(policy.contains("[\\\\p{IsHan}]{2,}|[A-Za-z0-9_./:-]{2,}")),
                () -> assertTrue(policy.contains("chineseNgrams(token, 2)")),
                () -> assertTrue(policy.contains("chineseNgrams(token, 3)")),
                () -> assertTrue(policy.contains("splitAsciiToken")),
                () -> assertTrue(policy.contains("candidateTerms")),
                () -> assertTrue(policy.contains("LinkedHashSet")),
                () -> assertTrue(policy.contains("isTechnicalTerm")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("IRagKnowledgeRepository")),
                () -> assertFalse(policy.contains("RagLexicalChunkRecord")),
                () -> assertFalse(policy.contains("Document")),
                () -> assertFalse(policy.contains("HttpClient")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")));
    }

    @Test
    void repositoryLoadingAndBm25ScoringMustRemainInsideDedicatedAdapter() throws IOException {
        String recall = read(BM25_RECALL);

        assertAll(
                () -> assertTrue(recall.contains("class RagBm25LexicalRecall")),
                () -> assertTrue(recall.contains("implements RagRecallCoordinator.LexicalRecall")),
                () -> assertTrue(recall.contains("IRagKnowledgeRepository")),
                () -> assertTrue(recall.contains("searchLexicalCandidates")),
                () -> assertTrue(recall.contains("MAX_CANDIDATE_TERMS = 32")),
                () -> assertTrue(recall.contains("K1 = 1.5d")),
                () -> assertTrue(recall.contains("B = 0.75d")),
                () -> assertTrue(recall.contains("Math.max(100, topK * 50)")),
                () -> assertTrue(recall.contains("bm25_score")),
                () -> assertTrue(recall.contains("retrieval_source")),
                () -> assertTrue(recall.contains("ops-chat-memory")),
                () -> assertTrue(recall.contains("RAG BM25 候选召回失败，将降级为空结果")),
                () -> assertFalse(recall.contains("VectorStore")),
                () -> assertFalse(recall.contains("EmbeddingModel")),
                () -> assertFalse(recall.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(recall.contains("HttpClient")),
                () -> assertFalse(recall.contains("com.alibaba.fastjson")),
                () -> assertFalse(recall.contains("ChatClientRequest")));
    }

    @Test
    void coordinatorMustOwnLexicalRecallAndAdvisorMustNotReimplementBm25() throws IOException {
        String coordinator = read(COORDINATOR);
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(coordinator.contains("private final LexicalRecall lexicalRecall")),
                () -> assertTrue(coordinator.contains("new RagBm25LexicalRecall(ragKnowledgeRepository)")),
                () -> assertTrue(coordinator.contains("lexicalRecall.search")),
                () -> assertFalse(advisor.contains("bm25Search")),
                () -> assertFalse(advisor.contains("bm25Score")),
                () -> assertFalse(advisor.contains("loadBm25Chunks")),
                () -> assertFalse(advisor.contains("searchLexicalCandidates")),
                () -> assertFalse(advisor.contains("RagLexicalChunkRecord")),
                () -> assertFalse(advisor.contains("TOKEN_PATTERN")),
                () -> assertFalse(advisor.contains("private List<String> tokenize")),
                () -> assertFalse(advisor.contains("private record Bm25Chunk")),
                () -> assertFalse(advisor.contains("RagLexicalTextPolicy lexicalTextPolicy")),
                () -> assertFalse(advisor.contains("lexicalTextPolicy.tokenize")),
                () -> assertTrue(advisor.lines().count() < 600));
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
