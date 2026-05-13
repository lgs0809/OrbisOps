package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagLexicalTextPolicyTest {

    private final RagLexicalTextPolicy policy = new RagLexicalTextPolicy();

    @Test
    void shouldPreserveChineseNgramsAndAsciiTechnicalTokenExpansion() {
        List<String> tokens = policy.tokenize("锁单失败 ERR_LOCK_001 /api/demo-project/join");

        assertTrue(tokens.contains("锁单失败"));
        assertTrue(tokens.containsAll(List.of("锁单", "单失", "失败")));
        assertTrue(tokens.containsAll(List.of("锁单失", "单失败")));
        assertTrue(tokens.contains("err_lock_001"));
        assertTrue(tokens.containsAll(List.of("err", "lock", "001")));
        assertTrue(tokens.contains("/api/demo-project/join"));
        assertTrue(tokens.containsAll(List.of("api", "demo", "project", "join")));
    }

    @Test
    void shouldPrioritizeTechnicalTermsThenKeepNaturalLanguageOrderAndDeduplicate() {
        List<String> selected = policy.candidateTerms(
                List.of("锁单失败", "err_lock_001", "库存异常", "traceid", "锁单失败", "err_lock_001"),
                4);

        assertEquals(List.of("err_lock_001", "traceid", "锁单失败", "库存异常"), selected);
    }

    @Test
    void shouldRespectCandidateTermLimitWithoutMutatingInputOrder() {
        List<String> terms = List.of("自然语言", "trace_id", "order_id", "错误定位", "timeout");

        assertEquals(List.of("trace_id", "order_id", "timeout"), policy.candidateTerms(terms, 3));
        assertEquals(List.of("自然语言", "trace_id", "order_id", "错误定位", "timeout"), terms);
    }

    @Test
    void shouldReturnEmptyForBlankTextOrUnavailableCandidateInput() {
        assertEquals(List.of(), policy.tokenize(null));
        assertEquals(List.of(), policy.tokenize("\u2003\t\n"));
        assertEquals(List.of(), policy.candidateTerms(null, 32));
        assertEquals(List.of(), policy.candidateTerms(List.of(), 32));
        assertEquals(List.of(), policy.candidateTerms(List.of("traceid"), 0));
    }

    @Test
    void shouldIgnoreSingleCharacterTokensAndNormalizeAsciiCase() {
        assertEquals(List.of(), policy.tokenize("中 a 1 /"));
        assertEquals(List.of("error", "error", "traceid", "traceid"),
                policy.tokenize("ERROR TraceId"));
    }
}
