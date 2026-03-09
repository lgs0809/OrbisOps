package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalPlanPolicyBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String POLICY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRetrievalPlanPolicy.java";
    private static final String PLAN = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRetrievalPlan.java";

    @Test
    void retrievalPlanningRulesMustRemainInsideFrameworkNeutralPolicy() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("class RagRetrievalPlanPolicy")),
                () -> assertTrue(policy.contains("qa_retrieval_mode")),
                () -> assertTrue(policy.contains("qa_dynamic_search")),
                () -> assertTrue(policy.contains("qa_vector_top_k")),
                () -> assertTrue(policy.contains("qa_bm25_top_k")),
                () -> assertTrue(policy.contains("qa_final_top_k")),
                () -> assertTrue(policy.contains("qa_max_context_chars")),
                () -> assertTrue(policy.contains("qa_rerank_candidate_top_k")),
                () -> assertTrue(policy.contains("RERANK_PROVIDERS")),
                () -> assertTrue(policy.contains("inferRetrievalMode")),
                () -> assertTrue(policy.contains("frameworkDefaultTopK")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("ChatClientRequest")),
                () -> assertFalse(policy.contains("SearchRequest")),
                () -> assertFalse(policy.contains("VectorStore")),
                () -> assertFalse(policy.contains("java.net.http")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("IRagKnowledgeRepository")),
                () -> assertFalse(policy.contains("RagMultimodalEmbeddingService")),
                () -> assertFalse(policy.contains("Document")));
    }

    @Test
    void retrievalPlanMustRemainTypedAndFrameworkNeutral() throws IOException {
        String plan = read(PLAN);

        assertAll(
                () -> assertTrue(plan.contains("public record RagRetrievalPlan")),
                () -> assertTrue(plan.contains("String mode")),
                () -> assertTrue(plan.contains("int vectorTopK")),
                () -> assertTrue(plan.contains("int bm25TopK")),
                () -> assertTrue(plan.contains("int finalTopK")),
                () -> assertTrue(plan.contains("boolean rerankEnabled")),
                () -> assertTrue(plan.contains("String rerankProvider")),
                () -> assertFalse(plan.contains("org.springframework")),
                () -> assertFalse(plan.contains("java.net.http")),
                () -> assertFalse(plan.contains("com.alibaba.fastjson")));
    }

    @Test
    void advisorMustDelegatePlanningAndNotReimplementPolicy() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("RagRetrievalPlanPolicy")),
                () -> assertTrue(advisor.contains("retrievalPlanPolicy.resolve")),
                () -> assertTrue(advisor.contains("SearchRequest.DEFAULT_TOP_K")),
                () -> assertTrue(advisor.contains("RagRetrievalPlan plan")),
                () -> assertFalse(advisor.contains("buildRetrievalPlan")),
                () -> assertFalse(advisor.contains("inferRetrievalMode")),
                () -> assertFalse(advisor.contains("private record RetrievalPlan")),
                () -> assertFalse(advisor.contains("DEFAULT_MAX_CONTEXT_CHARS")),
                () -> assertFalse(advisor.contains("RERANK_PROVIDERS")),
                () -> assertTrue(advisor.lines().count() < 980));
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
