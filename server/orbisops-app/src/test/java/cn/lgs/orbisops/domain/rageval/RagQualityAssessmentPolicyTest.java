package cn.lgs.orbisops.domain.rageval;

import cn.lgs.orbisops.domain.rageval.model.RagEvalProbeAssessment;
import cn.lgs.orbisops.domain.rageval.model.RagEvalRunAssessment;
import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagQualityAssessmentPolicyTest {

    private final RagQualityAssessmentPolicy policy = new RagQualityAssessmentPolicy();

    @Test
    void scoresContentCoverageReciprocalRankAndPassGate() {
        RagEvalProbeAssessment result = policy.assessProbe(
                List.of(
                        "订单超时排查包含 traceId 与 timeout",
                        "补充日志说明"),
                "订单超时",
                List.of("traceId", "timeout", "重试"));

        assertEquals(List.of(4D, 0D), result.hitScores());
        assertEquals(List.of("traceId", "timeout"), result.coveredKeywords());
        assertEquals(List.of("重试"), result.missingKeywords());
        assertEquals(2D / 3D, result.keywordCoverage(), 0.000001D);
        assertEquals(1D, result.reciprocalRank(), 0.000001D);
        assertTrue(result.passed());
        assertEquals("命中结果可用，且已复用线上 RagAnswerAdvisor 检索链路。", result.recommendation());
    }

    @Test
    void noHitAndLowCoverageProduceStableRecommendations() {
        RagEvalProbeAssessment noHit = policy.assessProbe(List.of(), "问题", List.of("证据"));
        RagEvalProbeAssessment lowCoverage = policy.assessProbe(
                List.of("只包含证据A"),
                "其他问题",
                List.of("证据A", "证据B"));

        assertFalse(noHit.passed());
        assertEquals(0D, noHit.keywordCoverage());
        assertTrue(noHit.recommendation().startsWith("未命中 chunk"));
        assertFalse(lowCoverage.passed());
        assertEquals(0.5D, lowCoverage.keywordCoverage());
        assertTrue(lowCoverage.recommendation().contains("证据B"));
    }

    @Test
    void emptyExpectedKeywordsPassWhenAtLeastOneHitExists() {
        RagEvalProbeAssessment result = policy.assessProbe(
                List.of("任意命中"),
                "不存在于正文的查询",
                List.of());

        assertEquals(1D, result.keywordCoverage());
        assertTrue(result.passed());
        assertEquals(0D, result.reciprocalRank());
    }

    @Test
    void aggregatesRunMetricsFromProbeAssessments() {
        RagEvalProbeAssessment passed = policy.assessProbe(
                List.of("query keyword"), "query", List.of("keyword"));
        RagEvalProbeAssessment missed = policy.assessProbe(
                List.of(), "query", List.of("keyword"));

        RagEvalRunAssessment aggregate = policy.aggregate(List.of(passed, missed));

        assertEquals(2, aggregate.caseCount());
        assertEquals(0.5D, aggregate.hitRate());
        assertEquals(0.5D, aggregate.averageKeywordCoverage());
        assertEquals(0.5D, aggregate.meanReciprocalRank());
        assertEquals(1L, aggregate.passedCount());
    }
}
