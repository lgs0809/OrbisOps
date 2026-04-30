package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseResult;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseType;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationReport;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingEvaluationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillRoutingEvaluationPolicyTest {

    @Test
    void evaluationMustProduceTopKRatesAndConfusionEdges() {
        SkillRoutingEvaluationReport report = new SkillRoutingEvaluationPolicy().evaluate(List.of(
                result("p1", SkillRoutingEvaluationCaseType.POSITIVE,
                        "skill-a", List.of("skill-a", "skill-b"), 0.9, 0.4, false),
                result("hp1", SkillRoutingEvaluationCaseType.HARD_POSITIVE,
                        "skill-b", List.of(), 0.3, 0.2, true),
                result("n1", SkillRoutingEvaluationCaseType.NEGATIVE,
                        "", List.of("skill-c"), 0.7, 0.5, false),
                result("o1", SkillRoutingEvaluationCaseType.OUT_OF_SCOPE,
                        "", List.of(), 0.2, 0.1, true),
                result("c1", SkillRoutingEvaluationCaseType.CONFUSING_NEIGHBOR,
                        "skill-a", List.of("skill-b", "skill-a"), 0.80, 0.79, false)
        ), Instant.parse("2026-08-02T02:00:00Z"));

        assertEquals(5, report.metrics().caseCount());
        assertEquals(3, report.metrics().positiveCount());
        assertEquals(2, report.metrics().negativeCount());
        assertEquals(1, report.metrics().top1CorrectCount());
        assertEquals(2, report.metrics().top3CorrectCount());
        assertEquals(1, report.metrics().falsePositiveCount());
        assertEquals(1, report.metrics().falseNegativeCount());
        assertEquals(1, report.metrics().shadowingCount());
        assertEquals(0.5, report.metrics().noSkillPrecision(), 1e-9);
        assertEquals(List.of(
                        "__NO_SKILL__->skill-c",
                        "skill-a->skill-b",
                        "skill-b->__NO_SKILL__"),
                report.confusionEdges().stream().map(edge -> edge.edgeKey()).toList());
    }

    @Test
    void malformedScoreOrEmptyCasesMustFailClosed() {
        SkillRoutingEvaluationPolicy evaluation = new SkillRoutingEvaluationPolicy();
        assertThrows(IllegalArgumentException.class, () -> evaluation.evaluate(
                List.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> result(
                "bad", SkillRoutingEvaluationCaseType.POSITIVE,
                "skill-a", List.of("skill-a"), 0.4, 0.5, false));
    }

    private SkillRoutingEvaluationCaseResult result(
            String id,
            SkillRoutingEvaluationCaseType type,
            String expected,
            List<String> ranked,
            double top1,
            double top2,
            boolean noSkill) {
        return new SkillRoutingEvaluationCaseResult(
                id, type, expected, ranked, top1, top2, noSkill);
    }
}
