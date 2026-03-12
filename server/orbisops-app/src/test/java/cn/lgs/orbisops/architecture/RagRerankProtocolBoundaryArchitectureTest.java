package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRerankProtocolBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String PROTOCOL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRerankProtocol.java";

    @Test
    void rerankConfigurationHttpJsonMappingAndDegradationMustRemainInsideProtocol() throws IOException {
        String protocol = read(PROTOCOL);

        assertAll(
                () -> assertTrue(protocol.contains("class RagRerankProtocol")),
                () -> assertTrue(protocol.contains("qa_rerank_fail_on_degradation")),
                () -> assertTrue(protocol.contains("qa_rerank_error")),
                () -> assertTrue(protocol.contains("qa_rerank_degraded")),
                () -> assertTrue(protocol.contains("case \"voyage\"")),
                () -> assertTrue(protocol.contains("case \"cohere\"")),
                () -> assertTrue(protocol.contains("top_k")),
                () -> assertTrue(protocol.contains("top_n")),
                () -> assertTrue(protocol.contains("return_documents")),
                () -> assertTrue(protocol.contains("truncation")),
                () -> assertTrue(protocol.contains("Authorization")),
                () -> assertTrue(protocol.contains("Bearer ")),
                () -> assertTrue(protocol.contains("HttpClient.Version.HTTP_1_1")),
                () -> assertTrue(protocol.contains("REQUEST_TIMEOUT_SECONDS = 45")),
                () -> assertTrue(protocol.contains("JSONObject.parseObject")),
                () -> assertTrue(protocol.contains("relevance_score")),
                () -> assertTrue(protocol.contains("reranked")),
                () -> assertTrue(protocol.contains("rerank_provider")),
                () -> assertTrue(protocol.contains("rerank_score")),
                () -> assertTrue(protocol.contains("thenComparingInt(ScoredDocument::originalIndex)")),
                () -> assertTrue(protocol.contains("interface HttpTransport")),
                () -> assertFalse(protocol.contains("IRagKnowledgeRepository")),
                () -> assertFalse(protocol.contains("VectorStore")),
                () -> assertFalse(protocol.contains("EmbeddingModel")),
                () -> assertFalse(protocol.contains("RagRecallCoordinator")),
                () -> assertFalse(protocol.contains("RagReciprocalRankFusion")),
                () -> assertFalse(protocol.contains("qa_mmr")),
                () -> assertFalse(protocol.contains("ChatClientRequest")));
    }

    @Test
    void advisorMustDelegateRerankWithoutOwningTransportOrProviderProtocol() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("RagRerankProtocol rerankProtocol")),
                () -> assertFalse(advisor.contains("rerankProtocol.rerank")),
                () -> assertFalse(advisor.contains("rerankDocuments")),
                () -> assertFalse(advisor.contains("rejectRerankIfStrict")),
                () -> assertFalse(advisor.contains("voyageRerank")),
                () -> assertFalse(advisor.contains("cohereRerank")),
                () -> assertFalse(advisor.contains("applyRerankScores")),
                () -> assertFalse(advisor.contains("postJson")),
                () -> assertFalse(advisor.contains("HttpClient")),
                () -> assertFalse(advisor.contains("HttpRequest")),
                () -> assertFalse(advisor.contains("HttpResponse")),
                () -> assertFalse(advisor.contains("JSONObject")),
                () -> assertFalse(advisor.contains("JSONArray")),
                () -> assertFalse(advisor.contains("qa_rerank_")),
                () -> assertFalse(advisor.contains("rerank_provider")),
                () -> assertFalse(advisor.contains("rerank_score")),
                () -> assertFalse(advisor.contains("ScoredDocument")),
                () -> assertFalse(advisor.contains("limitContext")),
                () -> assertTrue(advisor.contains("contextRenderer.render")),
                () -> assertTrue(advisor.lines().count() < 300));
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
