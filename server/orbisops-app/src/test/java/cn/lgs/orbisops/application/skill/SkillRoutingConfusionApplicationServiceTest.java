package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillConfusionEdge;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseResult;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationCaseType;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingEvaluationReport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillRoutingConfusionApplicationServiceTest {

    @Test
    void evaluationMustReplaceGraphWithDeterministicEdges() {
        InMemoryGraphPort port = new InMemoryGraphPort();
        SkillRoutingConfusionApplicationService service =
                new SkillRoutingConfusionApplicationService(port);

        SkillRoutingEvaluationReport report = service.evaluate(List.of(
                new SkillRoutingEvaluationCaseResult(
                        "case-2", SkillRoutingEvaluationCaseType.NEGATIVE, "",
                        List.of("skill-b"), 0.7, 0.5, false),
                new SkillRoutingEvaluationCaseResult(
                        "case-1", SkillRoutingEvaluationCaseType.POSITIVE, "skill-a",
                        List.of("skill-b", "skill-a"), 0.8, 0.7, false)
        ), Instant.parse("2026-08-02T03:00:00Z"));

        assertEquals(report.confusionEdges(), port.edges);
        assertEquals(List.of("__NO_SKILL__->skill-b", "skill-a->skill-b"),
                port.edges.stream().map(SkillConfusionEdge::edgeKey).toList());
        assertEquals(port.edges, service.neighbors("skill-b", 0));
        assertEquals(20, port.lastLimit);
    }

    private static final class InMemoryGraphPort implements SkillConfusionGraphPort {
        private List<SkillConfusionEdge> edges = new ArrayList<>();
        private int lastLimit;

        @Override
        public void replaceEdges(List<SkillConfusionEdge> edges) {
            this.edges = List.copyOf(edges);
        }

        @Override
        public List<SkillConfusionEdge> neighbors(String skillId, int limit) {
            lastLimit = limit;
            return edges.stream().filter(edge -> edge.expectedSkillId().equals(skillId)
                    || edge.selectedSkillId().equals(skillId)).toList();
        }
    }
}
