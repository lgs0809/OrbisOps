package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsNodeRagRetrievalPlannerTest {

    private final OpsNodeRagRetrievalPlanner planner =
            new OpsNodeRagRetrievalPlanner();

    @Test
    void streamingPlanMustUseTtftLimitsAndDisableRerank() {
        OpsNodeRagAdvisorFactory.Resources resources =
                new OpsNodeRagAdvisorFactory.Resources(
                        null, null, null,
                        true, true, true, true, true);

        OpsNodeRagRetrievalPlanner.Plan plan = planner.plan(
                "prompt",
                "query",
                true,
                "project-a",
                "knowledge-a",
                "PROJECT",
                resources,
                settings());

        assertTrue(plan.streamingOptimized());
        assertFalse(plan.rerankEnabled());
        assertTrue(plan.llmQueryRewriteEnabled());
        assertEquals(3, plan.vectorTopK());
        assertEquals(5, plan.bm25TopK());
        assertEquals(2, plan.finalTopK());
    }

    @Test
    void projectFilterMustEscapeIdentityValues() {
        String expression = planner.knowledgeFilterExpression(
                "project'one",
                "knowledge\\one",
                "PROJECT");

        assertEquals(
                "knowledge == 'knowledge\\\\one' && knowledge_scope == 'PROJECT' && project_id == 'project\\'one'",
                expression);
    }

    @Test
    void advisorContextMustCarryTypedPlanAndFailurePolicy() {
        OpsNodeRagAdvisorFactory.Resources resources =
                new OpsNodeRagAdvisorFactory.Resources(
                        null, null, null,
                        true, false, true, true, true);
        OpsNodeRagRetrievalPlanner.Plan plan = planner.plan(
                null,
                "query",
                false,
                "",
                "knowledge-a",
                "GLOBAL",
                resources,
                settings());

        var context = planner.advisorContext(plan, settings());

        assertEquals("bm25", context.get("qa_retrieval_mode"));
        assertEquals(true, context.get("qa_rerank_enabled"));
        assertEquals(true, context.get("qa_fail_on_degradation"));
        assertEquals(
                "knowledge == 'knowledge-a' && knowledge_scope == 'GLOBAL'",
                context.get("qa_filter_expression"));
    }

    private OpsNodeRagSettings settings() {
        return new OpsNodeRagSettings(
                new OpsNodeRagSettings.Rerank(
                        true,
                        "cohere",
                        "http://localhost",
                        "key",
                        "v1/rerank",
                        "rerank-model",
                        10,
                        4,
                        1000),
                new OpsNodeRagSettings.Ttft(
                        true,
                        true,
                        3,
                        5,
                        2,
                        true),
                new OpsNodeRagSettings.QueryRewrite(
                        "hybrid",
                        true,
                        "http://localhost",
                        "key",
                        "v1/chat/completions",
                        "chat-model",
                        4,
                        2,
                        18,
                        true,
                        2),
                true);
    }
}
