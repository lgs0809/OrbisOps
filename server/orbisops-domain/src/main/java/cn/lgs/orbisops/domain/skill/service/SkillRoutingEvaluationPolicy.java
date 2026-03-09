package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillConfusionEdge;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseResult;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseType;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationReport;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillRoutingEvaluationPolicy {

    public static final String NO_SKILL = "__NO_SKILL__";

    public SkillRoutingEvaluationReport evaluate(
            List<SkillRoutingEvaluationCaseResult> cases,
            Instant evaluatedAt) {
        if (evaluatedAt == null) throw new IllegalArgumentException("SKILL_ROUTING_EVALUATED_AT_REQUIRED");
        List<SkillRoutingEvaluationCaseResult> safe = cases == null ? List.of() : cases.stream()
                .sorted(Comparator.comparing(SkillRoutingEvaluationCaseResult::caseId))
                .toList();
        if (safe.isEmpty()) throw new IllegalArgumentException("SKILL_ROUTING_CASES_REQUIRED");

        int positives = 0;
        int negatives = 0;
        int top1 = 0;
        int top3 = 0;
        int fp = 0;
        int fn = 0;
        int shadowing = 0;
        int noSkillTruePositive = 0;
        int noSkillSelected = 0;
        double marginSum = 0D;
        Map<String, EdgeAccumulator> edges = new LinkedHashMap<>();

        for (SkillRoutingEvaluationCaseResult item : safe) {
            boolean positive = positive(item.caseType());
            marginSum += item.margin();
            if (item.noSkillSelected()) noSkillSelected++;
            if (positive) {
                positives++;
                if (item.top1Correct()) top1++;
                if (item.top3Correct()) top3++;
                if (item.noSkillSelected()) {
                    fn++;
                    edge(edges, item.expectedSkillId(), NO_SKILL, item.margin()).falseNegatives++;
                } else if (!item.top1Correct()) {
                    shadowing++;
                    EdgeAccumulator edge = edge(
                            edges, item.expectedSkillId(), item.selectedSkillId(), item.margin());
                    edge.shadowing++;
                    edge.falseNegatives++;
                    edge.falsePositives++;
                }
            } else {
                negatives++;
                if (item.noSkillSelected()) {
                    noSkillTruePositive++;
                } else {
                    fp++;
                    edge(edges, NO_SKILL, item.selectedSkillId(), item.margin()).falsePositives++;
                }
            }
        }

        SkillRoutingEvaluationMetrics metrics = new SkillRoutingEvaluationMetrics(
                safe.size(), positives, negatives, top1, top3, fp, fn, shadowing,
                noSkillTruePositive, noSkillSelected,
                ratio(marginSum, safe.size()),
                ratio(top1, positives), ratio(top3, positives),
                ratio(fp, negatives), ratio(fn, positives),
                ratio(noSkillTruePositive, noSkillSelected));
        List<SkillConfusionEdge> confusionEdges = edges.values().stream()
                .sorted(Comparator.comparing(EdgeAccumulator::edgeKey))
                .map(edge -> new SkillConfusionEdge(
                        edge.expectedSkillId, edge.selectedSkillId,
                        edge.falsePositives, edge.falseNegatives, edge.shadowing,
                        ratio(edge.marginSum, edge.observations), evaluatedAt))
                .toList();
        return new SkillRoutingEvaluationReport(metrics, confusionEdges);
    }

    private EdgeAccumulator edge(
            Map<String, EdgeAccumulator> edges,
            String expected,
            String selected,
            double margin) {
        String key = expected + "->" + selected;
        EdgeAccumulator value = edges.computeIfAbsent(
                key, ignored -> new EdgeAccumulator(expected, selected));
        value.marginSum += margin;
        value.observations++;
        return value;
    }

    private boolean positive(SkillRoutingEvaluationCaseType type) {
        return type == SkillRoutingEvaluationCaseType.POSITIVE
                || type == SkillRoutingEvaluationCaseType.HARD_POSITIVE
                || type == SkillRoutingEvaluationCaseType.CONFUSING_NEIGHBOR;
    }

    private double ratio(double numerator, int denominator) {
        if (denominator <= 0) return 0D;
        return Math.max(0D, Math.min(1D, numerator / denominator));
    }

    private static final class EdgeAccumulator {
        private final String expectedSkillId;
        private final String selectedSkillId;
        private int falsePositives;
        private int falseNegatives;
        private int shadowing;
        private int observations;
        private double marginSum;

        private EdgeAccumulator(String expectedSkillId, String selectedSkillId) {
            this.expectedSkillId = expectedSkillId;
            this.selectedSkillId = selectedSkillId;
        }

        private String edgeKey() {
            return expectedSkillId + "->" + selectedSkillId;
        }
    }
}
