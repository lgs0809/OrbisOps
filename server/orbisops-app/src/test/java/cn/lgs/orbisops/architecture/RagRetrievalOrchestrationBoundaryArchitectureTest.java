package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalOrchestrationBoundaryArchitectureTest {

    private static final String ADVISOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagAnswerAdvisor.java";
    private static final String ORCHESTRATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/advisor/RagRetrievalOrchestrator.java";

    @Test
    void filterRewriteRecallFusionRetryRerankMmrAndFinalLimitMustRemainInsideOrchestrator() throws IOException {
        String orchestrator = read(ORCHESTRATOR);

        assertAll(
                () -> assertTrue(orchestrator.contains("class RagRetrievalOrchestrator")),
                () -> assertTrue(orchestrator.contains("qa_filter_expression")),
                () -> assertTrue(orchestrator.contains("queryRewritePolicy.initialDecision")),
                () -> assertTrue(orchestrator.contains("llmQueryRewriteProtocol.rewrite")),
                () -> assertTrue(orchestrator.contains("qa_query_rewrite_fail_on_degradation")),
                () -> assertTrue(orchestrator.contains("qa_llm_query_rewrite_error")),
                () -> assertTrue(orchestrator.contains("qa_llm_query_rewrite_degraded")),
                () -> assertTrue(orchestrator.contains("recallCoordinator.recall")),
                () -> assertTrue(orchestrator.contains("reciprocalRankFusion.fuse")),
                () -> assertTrue(orchestrator.contains("shouldRetryAfterLowRecall")),
                () -> assertTrue(orchestrator.contains("qa_low_recall_rewrite_applied")),
                () -> assertTrue(orchestrator.contains("rerankProtocol.rerank")),
                () -> assertTrue(orchestrator.contains("mmrDiversitySelector.select")),
                () -> assertTrue(orchestrator.contains("limit(plan.finalTopK())")),
                () -> assertFalse(orchestrator.contains("ChatClientRequest")),
                () -> assertFalse(orchestrator.contains("ChatClientResponse")),
                () -> assertFalse(orchestrator.contains("AdvisorChain")),
                () -> assertFalse(orchestrator.contains("Prompt.builder")),
                () -> assertFalse(orchestrator.contains("question_answer_context")),
                () -> assertFalse(orchestrator.contains("JSON.toJSONString")),
                () -> assertFalse(orchestrator.contains("HttpClient")),
                () -> assertFalse(orchestrator.contains("VectorStore")),
                () -> assertFalse(orchestrator.contains("IRagKnowledgeRepository")));
    }

    @Test
    void advisorMustRemainSpringAiLifecycleFacadeAndDelegateWholeRetrievalPipeline() throws IOException {
        String advisor = read(ADVISOR);

        assertAll(
                () -> assertTrue(advisor.contains("implements BaseAdvisor")),
                () -> assertTrue(advisor.contains("RagRetrievalOrchestrator retrievalOrchestrator")),
                () -> assertTrue(advisor.contains("retrievalOrchestrator.retrieve")),
                () -> assertTrue(advisor.contains("RagRetrievalPlanPolicy retrievalPlanPolicy")),
                () -> assertTrue(advisor.contains("RagContextRenderer contextRenderer")),
                () -> assertTrue(advisor.contains("ChatClientRequest before")),
                () -> assertTrue(advisor.contains("ChatClientResponse after")),
                () -> assertTrue(advisor.contains("adviseCall")),
                () -> assertTrue(advisor.contains("adviseStream")),
                () -> assertFalse(advisor.contains("retrieveDocuments")),
                () -> assertFalse(advisor.contains("rewriteQueries")),
                () -> assertFalse(advisor.contains("llmRewriteQueries")),
                () -> assertFalse(advisor.contains("rejectRewriteIfStrict")),
                () -> assertFalse(advisor.contains("qa_filter_expression")),
                () -> assertFalse(advisor.contains("qa_rewrite_queries")),
                () -> assertFalse(advisor.contains("qa_low_recall_rewrite_applied")),
                () -> assertFalse(advisor.contains("recallCoordinator.recall")),
                () -> assertFalse(advisor.contains("reciprocalRankFusion.fuse")),
                () -> assertFalse(advisor.contains("rerankProtocol.rerank")),
                () -> assertFalse(advisor.contains("mmrDiversitySelector.select")),
                () -> assertFalse(advisor.contains("RagQueryRewritePolicy")),
                () -> assertFalse(advisor.contains("RagLlmQueryRewriteProtocol")),
                () -> assertTrue(advisor.lines().count() < 160));
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
