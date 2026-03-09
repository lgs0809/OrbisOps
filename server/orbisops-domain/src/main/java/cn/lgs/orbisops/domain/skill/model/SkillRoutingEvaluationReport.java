package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillRoutingEvaluationReport(
        SkillRoutingEvaluationMetrics metrics,
        List<SkillConfusionEdge> confusionEdges
) {

    public SkillRoutingEvaluationReport {
        if (metrics == null) throw new IllegalArgumentException("SKILL_ROUTING_METRICS_REQUIRED");
        confusionEdges = confusionEdges == null ? List.of() : List.copyOf(confusionEdges);
    }
}
