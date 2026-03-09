package cn.lgs.orbisops.domain.memory;

import cn.lgs.orbisops.domain.memory.service.SemanticLexicalQueryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticLexicalQueryPolicyTest {

    private final SemanticLexicalQueryPolicy policy = new SemanticLexicalQueryPolicy();

    @Test
    void extractsChineseAndTechnicalTermsInStableDistinctOrder() {
        List<String> terms = policy.terms(
                "Join_metric_5XX 在 /API/group/join 升高，join_metric_5xx 需要排查 HTTP:500。");

        assertEquals(List.of(
                "join_metric_5xx",
                "/api/group/join",
                "升高",
                "需要排查",
                "http:500"), terms);
    }

    @Test
    void ignoresSingleCharacterTokensAndBlankInput() {
        assertEquals(List.of(), policy.terms(null));
        assertEquals(List.of(), policy.terms(" "));
        assertEquals(List.of(), policy.terms("a 中 1"));
        assertEquals("", policy.webSearchQuery("a 中 1"));
    }

    @Test
    void capsTermsAtTwelveAndRendersPostgresOrQuery() {
        String input = "t01 t02 t03 t04 t05 t06 t07 t08 t09 t10 t11 t12 t13 t14";

        List<String> terms = policy.terms(input);

        assertEquals(12, terms.size());
        assertEquals("t01", terms.get(0));
        assertEquals("t12", terms.get(11));
        assertEquals(String.join(" OR ", terms), policy.webSearchQuery(input));
    }

    @Test
    void caseNormalizationOccursBeforeDeduplication() {
        assertEquals(List.of("metric"), policy.terms("Metric metric METRIC"));
        assertEquals("metric", policy.webSearchQuery("Metric metric METRIC"));
    }
}
