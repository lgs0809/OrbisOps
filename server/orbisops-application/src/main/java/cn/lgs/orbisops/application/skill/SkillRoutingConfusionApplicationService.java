package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillConfusionEdge;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseResult;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationReport;
import cn.lgs.orbisops.domain.skill.service.SkillRoutingEvaluationPolicy;

import java.time.Instant;
import java.util.List;

public final class SkillRoutingConfusionApplicationService {

    private final SkillConfusionGraphPort graphPort;
    private final SkillRoutingEvaluationPolicy policy;

    public SkillRoutingConfusionApplicationService(SkillConfusionGraphPort graphPort) {
        this(graphPort, new SkillRoutingEvaluationPolicy());
    }

    SkillRoutingConfusionApplicationService(
            SkillConfusionGraphPort graphPort,
            SkillRoutingEvaluationPolicy policy) {
        if (graphPort == null || policy == null) {
            throw new IllegalArgumentException("SKILL_CONFUSION_DEPENDENCY_REQUIRED");
        }
        this.graphPort = graphPort;
        this.policy = policy;
    }

    public SkillRoutingEvaluationReport evaluate(
            List<SkillRoutingEvaluationCaseResult> cases,
            Instant evaluatedAt) {
        SkillRoutingEvaluationReport report = policy.evaluate(cases, evaluatedAt);
        graphPort.replaceEdges(report.confusionEdges());
        return report;
    }

    public List<SkillConfusionEdge> neighbors(String skillId, int limit) {
        String normalized = skillId == null ? "" : skillId.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_CONFUSION_SKILL_ID_REQUIRED");
        int bounded = limit <= 0 ? 20 : Math.min(limit, 200);
        return List.copyOf(graphPort.neighbors(normalized, bounded));
    }
}
