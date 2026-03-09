package cn.lgs.orbisops.domain.runtime.retrieval;

import cn.lgs.orbisops.domain.runtime.retrieval.service.KnowledgeRetrievalPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KnowledgeRetrievalPolicyTest {

    private final KnowledgeRetrievalPolicy policy = new KnowledgeRetrievalPolicy();

    @Test
    void runtimeFiltersPreferHybridThenLexicalThenVector() {
        assertEquals("hybrid", policy.retrievalModeForIteration(null, 1, true));
        assertEquals("bm25", policy.retrievalModeForIteration(null, 2, true));
        assertEquals("vector", policy.retrievalModeForIteration(null, 3, true));
    }

    @Test
    void broadQuestionsPreferAutoThenHybridThenVector() {
        assertEquals("auto", policy.retrievalModeForIteration(null, 1, false));
        assertEquals("hybrid", policy.retrievalModeForIteration(null, 2, false));
        assertEquals("vector", policy.retrievalModeForIteration(null, 3, false));
        assertEquals("bm25", policy.retrievalModeForIteration(null, 99, false));
    }

    @Test
    void preferredModeIsFirstButNeverDuplicated() {
        assertEquals("vector", policy.retrievalModeForIteration("vector", 1, true));
        assertEquals("hybrid", policy.retrievalModeForIteration("vector", 2, true));
        assertEquals("bm25", policy.retrievalModeForIteration("vector", 3, true));
    }

    @Test
    void queryFocusCanOnlyAppendInformation() {
        String base = "支付服务延迟升高";
        assertEquals(base, policy.buildQuery(base, false, "数据库"));
        assertEquals(base, policy.buildQuery(base, true, "支付服务"));
        String enriched = policy.buildQuery(base, true, "数据库慢查询");
        assertEquals(base + "\n检索焦点：数据库慢查询", enriched);
        assertFalse(enriched.startsWith("数据库慢查询"));
    }

    @Test
    void missingQuestionUsesStableFallback() {
        assertEquals(
                KnowledgeRetrievalPolicy.DEFAULT_QUERY,
                policy.buildQuery(null, false, null));
    }
}
