package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleOperation;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleSubject;
import cn.lgs.orbisops.domain.skill.model.SkillLineageEdge;
import cn.lgs.orbisops.domain.skill.model.SkillRetentionState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLifecycleApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T04:30:00Z");

    @Test
    void approvedProposalMustPersistProposalDecisionAndLineageInOrder() {
        RecordingPort port = new RecordingPort();
        SkillLifecycleApplicationService service = new SkillLifecycleApplicationService(port);

        SkillLifecycleDecision decision = service.evaluate(proposal(
                "proposal-1", SkillLifecycleOperation.PATCH,
                List.of(subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-a-v2"), true), NOW);

        assertTrue(decision.approved());
        assertEquals(List.of("proposal:proposal-1", "decision:proposal-1", "lineage:1"),
                port.events);
        assertEquals(decision.lineageEdges(), port.savedLineage);
    }

    @Test
    void rejectedProposalMustPersistDecisionWithoutLineage() {
        RecordingPort port = new RecordingPort();
        SkillLifecycleApplicationService service = new SkillLifecycleApplicationService(port);

        SkillLifecycleDecision decision = service.evaluate(proposal(
                "proposal-2", SkillLifecycleOperation.COMPRESS,
                List.of(subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-a-compressed"), false), NOW);

        assertFalse(decision.approved());
        assertEquals(List.of("proposal:proposal-2", "decision:proposal-2"), port.events);
        assertEquals(List.of(), port.savedLineage);
        assertTrue(decision.reasonCodes().contains(
                "SKILL_LIFECYCLE_BEHAVIOR_REPLAY_REQUIRED"));
    }

    @Test
    void lineageQueryMustApplyDefaultAndMaximumLimits() {
        RecordingPort port = new RecordingPort();
        port.queryResult = List.of(new SkillLineageEdge(
                "proposal-1", "skill-a@1", "skill-a-v2",
                cn.lgs.orbisops.domain.skill.model.SkillLineageRelation.PATCHED_FROM, NOW));
        SkillLifecycleApplicationService service = new SkillLifecycleApplicationService(port);

        assertEquals(port.queryResult, service.lineage("skill-a", 0));
        assertEquals(50, port.lastLimit);
        assertEquals(port.queryResult, service.lineage("skill-a", 1_000));
        assertEquals(500, port.lastLimit);
    }

    private SkillLifecycleProposal proposal(
            String proposalId,
            SkillLifecycleOperation operation,
            List<SkillLifecycleSubject> sources,
            List<String> targets,
            boolean replayPassed) {
        return new SkillLifecycleProposal(
                proposalId,
                operation,
                sources,
                targets,
                "lifecycle reason",
                List.of("evidence-1"),
                replayPassed ? "behavior-evaluation-1" : "",
                replayPassed,
                NOW);
    }

    private SkillLifecycleSubject subject(
            String skillId,
            boolean sealed,
            SkillRetentionState state) {
        return new SkillLifecycleSubject(
                skillId, 1, "hash-" + skillId, sealed, state);
    }

    private static final class RecordingPort implements SkillLifecyclePort {
        private final List<String> events = new ArrayList<>();
        private List<SkillLineageEdge> savedLineage = List.of();
        private List<SkillLineageEdge> queryResult = List.of();
        private int lastLimit;

        @Override
        public void saveProposal(SkillLifecycleProposal proposal) {
            events.add("proposal:" + proposal.proposalId());
        }

        @Override
        public void saveDecision(SkillLifecycleDecision decision) {
            events.add("decision:" + decision.proposalId());
        }

        @Override
        public void saveLineage(List<SkillLineageEdge> edges) {
            savedLineage = List.copyOf(edges);
            events.add("lineage:" + edges.size());
        }

        @Override
        public List<SkillLineageEdge> lineage(String skillId, int limit) {
            lastLimit = limit;
            return queryResult;
        }
    }
}
