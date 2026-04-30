package cn.lgs.orbisops.domain.skill;

import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleOperation;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleSubject;
import cn.lgs.orbisops.domain.skill.model.SkillLineageRelation;
import cn.lgs.orbisops.domain.skill.model.SkillRetentionState;
import cn.lgs.orbisops.domain.skill.service.SkillLifecyclePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillLifecyclePolicyTest {

    private final SkillLifecyclePolicy policy = new SkillLifecyclePolicy();
    private final Instant now = Instant.parse("2026-08-02T04:00:00Z");

    @Test
    void sealedSkillMustRejectInPlacePatchButAllowDerivedPatch() {
        SkillLifecycleSubject sealed = subject(
                "skill-a", true, SkillRetentionState.ACTIVE);
        SkillLifecycleDecision inPlace = policy.evaluate(proposal(
                "p1", SkillLifecycleOperation.PATCH, List.of(sealed),
                List.of("skill-a"), true, List.of("eval-1")), now);
        SkillLifecycleDecision derived = policy.evaluate(proposal(
                "p2", SkillLifecycleOperation.PATCH, List.of(sealed),
                List.of("skill-a-v2"), true, List.of("eval-2")), now);

        assertFalse(inPlace.approved());
        assertTrue(inPlace.reasonCodes().contains(
                "SKILL_LIFECYCLE_SEALED_IN_PLACE_MUTATION_FORBIDDEN"));
        assertTrue(derived.approved());
        assertEquals(SkillLineageRelation.PATCHED_FROM,
                derived.lineageEdges().get(0).relation());
        assertEquals("skill-a-v2", derived.lineageEdges().get(0).targetSkillId());
    }

    @Test
    void mergeAndSplitMustCreateCompleteStableLineage() {
        SkillLifecycleDecision merge = policy.evaluate(proposal(
                "merge-1", SkillLifecycleOperation.MERGE,
                List.of(subject("skill-b", false, SkillRetentionState.ACTIVE),
                        subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-ab"), true, List.of("eval-merge")), now);
        SkillLifecycleDecision split = policy.evaluate(proposal(
                "split-1", SkillLifecycleOperation.SPLIT,
                List.of(subject("skill-ab", false, SkillRetentionState.ACTIVE)),
                List.of("skill-b2", "skill-a2"), true, List.of("eval-split")), now);

        assertTrue(merge.approved());
        assertEquals(List.of("skill-a@1", "skill-b@1"),
                merge.lineageEdges().stream().map(edge -> edge.sourceReference()).toList());
        assertTrue(merge.lineageEdges().stream().allMatch(edge ->
                edge.relation() == SkillLineageRelation.MERGED_FROM));
        assertTrue(split.approved());
        assertEquals(List.of("skill-a2", "skill-b2"),
                split.lineageEdges().stream().map(edge -> edge.targetSkillId()).toList());
    }

    @Test
    void mutatingOperationsMustRequireReplayAndEvidence() {
        SkillLifecycleDecision decision = policy.evaluate(new SkillLifecycleProposal(
                "p1", SkillLifecycleOperation.COMPRESS,
                List.of(subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-a-compressed"), "compress repeated procedure",
                List.of(), "", false, now), now);

        assertFalse(decision.approved());
        assertTrue(decision.reasonCodes().contains(
                "SKILL_LIFECYCLE_BEHAVIOR_REPLAY_REQUIRED"));
        assertTrue(decision.reasonCodes().contains(
                "SKILL_LIFECYCLE_EVIDENCE_REQUIRED"));
    }

    @Test
    void pruneMustAdvanceOneRetentionStepWithoutPhysicalDelete() {
        assertEquals(SkillRetentionState.DEPRECATED,
                prune(SkillRetentionState.ACTIVE).targetRetentionState());
        assertEquals(SkillRetentionState.HIDDEN_FROM_ROUTING,
                prune(SkillRetentionState.DEPRECATED).targetRetentionState());
        assertEquals(SkillRetentionState.RETAINED_FOR_ROLLBACK,
                prune(SkillRetentionState.HIDDEN_FROM_ROUTING).targetRetentionState());
        assertEquals(SkillRetentionState.PURGE_ELIGIBLE,
                prune(SkillRetentionState.RETAINED_FOR_ROLLBACK).targetRetentionState());

        SkillLifecycleDecision terminal = prune(SkillRetentionState.PURGE_ELIGIBLE);
        assertFalse(terminal.approved());
        assertTrue(terminal.reasonCodes().contains(
                "SKILL_LIFECYCLE_ALREADY_PURGE_ELIGIBLE"));
        assertEquals(List.of(), terminal.lineageEdges());
    }

    @Test
    void invalidOperationShapesMustFailClosed() {
        SkillLifecycleDecision merge = policy.evaluate(proposal(
                "merge-1", SkillLifecycleOperation.MERGE,
                List.of(subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-ab"), true, List.of("eval-1")), now);
        SkillLifecycleDecision split = policy.evaluate(proposal(
                "split-1", SkillLifecycleOperation.SPLIT,
                List.of(subject("skill-a", false, SkillRetentionState.ACTIVE)),
                List.of("skill-a1"), true, List.of("eval-2")), now);

        assertFalse(merge.approved());
        assertFalse(split.approved());
    }

    private SkillLifecycleDecision prune(SkillRetentionState state) {
        return policy.evaluate(proposal(
                "prune-" + state.name(), SkillLifecycleOperation.PRUNE,
                List.of(subject("skill-a", false, state)), List.of("skill-a"),
                true, List.of("eval-prune")), now);
    }

    private SkillLifecycleProposal proposal(
            String id,
            SkillLifecycleOperation operation,
            List<SkillLifecycleSubject> sources,
            List<String> targets,
            boolean replayPassed,
            List<String> evidence) {
        return new SkillLifecycleProposal(
                id, operation, sources, targets, "lifecycle reason", evidence,
                replayPassed ? "behavior-evaluation-1" : "",
                replayPassed, now);
    }

    private SkillLifecycleSubject subject(
            String skillId,
            boolean sealed,
            SkillRetentionState state) {
        return new SkillLifecycleSubject(
                skillId, 1, "hash-" + skillId, sealed, state);
    }
}
