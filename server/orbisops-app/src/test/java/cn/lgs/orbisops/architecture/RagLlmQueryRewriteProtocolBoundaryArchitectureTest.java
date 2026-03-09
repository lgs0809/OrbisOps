package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagLlmQueryRewriteProtocolBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String PROTOCOL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagLlmQueryRewriteProtocol.java";
    private static final String RESULT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagLlmQueryRewriteResult.java";

    @Test
    void llmRewriteHttpJsonProtocolMustRemainInsideDedicatedAcl() throws IOException {
        String protocol = read(PROTOCOL);

        assertAll(
                () -> assertTrue(protocol.contains("class RagLlmQueryRewriteProtocol")),
                () -> assertTrue(protocol.contains("HttpClient")),
                () -> assertTrue(protocol.contains("HttpRequest")),
                () -> assertTrue(protocol.contains("Authorization")),
                () -> assertTrue(protocol.contains("com.alibaba.fastjson")),
                () -> assertTrue(protocol.contains("qa_llm_query_rewrite_base_url")),
                () -> assertTrue(protocol.contains("qa_llm_query_rewrite_api_key")),
                () -> assertTrue(protocol.contains("qa_llm_query_rewrite_timeout_seconds")),
                () -> assertTrue(protocol.contains("你是运维 RAG 检索 query rewrite 模块")),
                () -> assertTrue(protocol.contains("lowRecallRetry")),
                () -> assertTrue(protocol.contains("max_completion_tokens")),
                () -> assertTrue(protocol.contains("choices")),
                () -> assertTrue(protocol.contains("未返回 content")),
                () -> assertTrue(protocol.contains("未返回 queries")),
                () -> assertTrue(protocol.contains("JsonTransport")),
                () -> assertFalse(protocol.contains("org.springframework.ai")),
                () -> assertFalse(protocol.contains("IRagKnowledgeRepository")),
                () -> assertFalse(protocol.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(protocol.contains("VectorStore")),
                () -> assertFalse(protocol.contains("Document")),
                () -> assertFalse(protocol.contains("rerank_score")));
    }

    @Test
    void protocolResultMustRemainTypedAndDefensive() throws IOException {
        String result = read(RESULT);

        assertAll(
                () -> assertTrue(result.contains("public record RagLlmQueryRewriteResult")),
                () -> assertTrue(result.contains("List<String> queries")),
                () -> assertTrue(result.contains("boolean generated")),
                () -> assertTrue(result.contains("String degradationError")),
                () -> assertTrue(result.contains("Exception cause")),
                () -> assertTrue(result.contains("List.copyOf")),
                () -> assertTrue(result.contains("static RagLlmQueryRewriteResult success")),
                () -> assertTrue(result.contains("static RagLlmQueryRewriteResult degraded")),
                () -> assertFalse(result.contains("org.springframework")),
                () -> assertFalse(result.contains("com.alibaba.fastjson")),
                () -> assertFalse(result.contains("java.net.http")));
    }

    @Test
    void advisorMustMapTypedResultButNotReimplementRewriteProtocol() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("RagLlmQueryRewriteProtocol")),
                () -> assertFalse(advisor.contains("llmQueryRewriteProtocol.rewrite")),
                () -> assertFalse(advisor.contains("result.degradationError()")),
                () -> assertFalse(advisor.contains("rejectRewriteIfStrict")),
                () -> assertFalse(advisor.contains("qa_llm_query_rewrite_applied")),
                () -> assertFalse(advisor.contains("private JSONObject postJson")),
                () -> assertFalse(advisor.contains("Rerank")),
                () -> assertFalse(advisor.contains("qa_llm_query_rewrite_base_url")),
                () -> assertFalse(advisor.contains("qa_llm_query_rewrite_api_key")),
                () -> assertFalse(advisor.contains("你是运维 RAG 检索 query rewrite 模块")),
                () -> assertFalse(advisor.contains("未返回 choices")),
                () -> assertFalse(advisor.contains("未返回 content")),
                () -> assertFalse(advisor.contains("未返回 queries")),
                () -> assertFalse(advisor.contains("private JSONObject message")),
                () -> assertFalse(advisor.contains("private JSONObject parseJsonObject")),
                () -> assertTrue(advisor.lines().count() < 860));
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
