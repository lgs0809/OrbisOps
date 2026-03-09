package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQueryRewritePolicyBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String POLICY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagQueryRewritePolicy.java";
    private static final String DECISION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagQueryRewriteDecision.java";

    @Test
    void deterministicRewriteRulesMustRemainInsideFrameworkNeutralPolicy() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("class RagQueryRewritePolicy")),
                () -> assertTrue(policy.contains("initialDecision")),
                () -> assertTrue(policy.contains("shouldRetryAfterLowRecall")),
                () -> assertTrue(policy.contains("qa_query_rewrite_enabled")),
                () -> assertTrue(policy.contains("qa_query_rewrite_mode")),
                () -> assertTrue(policy.contains("qa_query_rewrite_max_queries")),
                () -> assertTrue(policy.contains("qa_llm_query_rewrite_enabled")),
                () -> assertTrue(policy.contains("qa_llm_query_rewrite_min_chars")),
                () -> assertTrue(policy.contains("qa_llm_query_rewrite_on_low_recall")),
                () -> assertTrue(policy.contains("query_time rows_examined")),
                () -> assertTrue(policy.contains("error exception stacktrace")),
                () -> assertTrue(policy.contains("prometheus metric qps")),
                () -> assertTrue(policy.contains("image screenshot diagram")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("java.net.http")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("IRagKnowledgeRepository")),
                () -> assertFalse(policy.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(policy.contains("Document")));
    }

    @Test
    void rewriteDecisionMustRemainTypedAndDefensive() throws IOException {
        String decision = read(DECISION);

        assertAll(
                () -> assertTrue(decision.contains("public record RagQueryRewriteDecision")),
                () -> assertTrue(decision.contains("List<String> queries")),
                () -> assertTrue(decision.contains("boolean rewriteEnabled")),
                () -> assertTrue(decision.contains("boolean llmEligible")),
                () -> assertTrue(decision.contains("List.copyOf")),
                () -> assertFalse(decision.contains("org.springframework")),
                () -> assertFalse(decision.contains("java.net.http")),
                () -> assertFalse(decision.contains("com.alibaba.fastjson")));
    }

    @Test
    void advisorMustDelegateDeterministicRewriteButRetainLlmProtocolForNextBoundary() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertFalse(advisor.contains("RagQueryRewritePolicy")),
                () -> assertFalse(advisor.contains("queryRewritePolicy.initialDecision")),
                () -> assertFalse(advisor.contains("queryRewritePolicy.shouldRetryAfterLowRecall")),
                () -> assertFalse(advisor.contains("llmRewriteQueries")),
                () -> assertFalse(advisor.contains("postJson")),
                () -> assertFalse(advisor.contains("private List<String> ruleRewriteQueries")),
                () -> assertFalse(advisor.contains("private boolean isComplexRewriteQuery")),
                () -> assertFalse(advisor.contains("private boolean shouldRetryLlmRewrite")),
                () -> assertFalse(advisor.contains("query_time rows_examined rows_sent")),
                () -> assertFalse(advisor.contains("image screenshot diagram architecture")),
                () -> assertTrue(advisor.lines().count() < 930));
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
