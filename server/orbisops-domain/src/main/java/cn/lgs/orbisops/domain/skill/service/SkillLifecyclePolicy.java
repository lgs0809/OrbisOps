package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleOperation;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleSubject;
import cn.lgs.orbisops.domain.skill.model.SkillLineageEdge;
import cn.lgs.orbisops.domain.skill.model.SkillLineageRelation;
import cn.lgs.orbisops.domain.skill.model.SkillRetentionState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SkillLifecyclePolicy {

    public SkillLifecycleDecision evaluate(
            SkillLifecycleProposal proposal,
            Instant decidedAt) {
        if (proposal == null || decidedAt == null) {
            throw new IllegalArgumentException("SKILL_LIFECYCLE_EVALUATION_INPUT_REQUIRED");
        }
        List<String> reasons = new ArrayList<>();
        validateCardinality(proposal, reasons);
        validateReplay(proposal, reasons);
        validateEvidence(proposal, reasons);
        validateSealedMutation(proposal, reasons);
        SkillRetentionState retention = retentionTarget(proposal, reasons);
        if (!reasons.isEmpty()) {
            return new SkillLifecycleDecision(
                    proposal.proposalId(), false, retention, List.of(), reasons);
        }
        return new SkillLifecycleDecision(
                proposal.proposalId(), true, retention,
                lineage(proposal, decidedAt), List.of());
    }

    private void validateCardinality(
            SkillLifecycleProposal proposal,
            List<String> reasons) {
        int sources = proposal.sources().size();
        int targets = proposal.targetSkillIds().size();
        switch (proposal.operation()) {
            case CREATE -> require(sources == 0 && targets == 1,
                    "SKILL_LIFECYCLE_CREATE_SHAPE_INVALID", reasons);
            case PATCH, COMPRESS, REVIVE, ROLLBACK -> require(sources == 1 && targets == 1,
                    "SKILL_LIFECYCLE_SINGLE_SOURCE_TARGET_REQUIRED", reasons);
            case MERGE -> require(sources >= 2 && targets == 1,
                    "SKILL_LIFECYCLE_MERGE_SHAPE_INVALID", reasons);
            case SPLIT -> require(sources == 1 && targets >= 2,
                    "SKILL_LIFECYCLE_SPLIT_SHAPE_INVALID", reasons);
            case DEPRECATE, PRUNE -> require(sources == 1 && targets <= 1,
                    "SKILL_LIFECYCLE_RETENTION_SHAPE_INVALID", reasons);
        }
        if ((proposal.operation() == SkillLifecycleOperation.DEPRECATE
                || proposal.operation() == SkillLifecycleOperation.PRUNE)
                && targets == 1
                && !proposal.sources().isEmpty()
                && !proposal.sources().get(0).skillId().equals(proposal.targetSkillIds().get(0))) {
            reasons.add("SKILL_LIFECYCLE_RETENTION_TARGET_MISMATCH");
        }
    }

    private void validateReplay(
            SkillLifecycleProposal proposal,
            List<String> reasons) {
        if (!requiresReplay(proposal.operation())) return;
        if (!proposal.behaviorReplayPassed()
                || proposal.behaviorEvaluationId().isBlank()) {
            reasons.add("SKILL_LIFECYCLE_BEHAVIOR_REPLAY_REQUIRED");
        }
    }

    private void validateEvidence(
            SkillLifecycleProposal proposal,
            List<String> reasons) {
        if (proposal.operation() != SkillLifecycleOperation.CREATE
                && proposal.operation() != SkillLifecycleOperation.DEPRECATE
                && proposal.evidenceIds().isEmpty()) {
            reasons.add("SKILL_LIFECYCLE_EVIDENCE_REQUIRED");
        }
    }

    private void validateSealedMutation(
            SkillLifecycleProposal proposal,
            List<String> reasons) {
        if (proposal.targetSkillIds().isEmpty()) return;
        boolean inPlace = proposal.sources().stream().anyMatch(source ->
                proposal.targetSkillIds().contains(source.skillId()));
        boolean sealedSource = proposal.sources().stream().anyMatch(SkillLifecycleSubject::sealed);
        if (sealedSource && inPlace
                && (proposal.operation() == SkillLifecycleOperation.PATCH
                || proposal.operation() == SkillLifecycleOperation.MERGE
                || proposal.operation() == SkillLifecycleOperation.COMPRESS)) {
            reasons.add("SKILL_LIFECYCLE_SEALED_IN_PLACE_MUTATION_FORBIDDEN");
        }
    }

    private SkillRetentionState retentionTarget(
            SkillLifecycleProposal proposal,
            List<String> reasons) {
        if (proposal.sources().isEmpty()) return null;
        SkillRetentionState current = proposal.sources().get(0).retentionState();
        return switch (proposal.operation()) {
            case DEPRECATE -> {
                if (current != SkillRetentionState.ACTIVE) {
                    reasons.add("SKILL_LIFECYCLE_DEPRECATE_STATE_INVALID");
                    yield current;
                }
                yield SkillRetentionState.DEPRECATED;
            }
            case PRUNE -> nextRetention(current, reasons);
            case REVIVE -> {
                if (current == SkillRetentionState.ACTIVE) {
                    reasons.add("SKILL_LIFECYCLE_REVIVE_STATE_INVALID");
                }
                yield SkillRetentionState.ACTIVE;
            }
            default -> current;
        };
    }

    private SkillRetentionState nextRetention(
            SkillRetentionState current,
            List<String> reasons) {
        return switch (current) {
            case ACTIVE -> SkillRetentionState.DEPRECATED;
            case DEPRECATED -> SkillRetentionState.HIDDEN_FROM_ROUTING;
            case HIDDEN_FROM_ROUTING -> SkillRetentionState.RETAINED_FOR_ROLLBACK;
            case RETAINED_FOR_ROLLBACK -> SkillRetentionState.PURGE_ELIGIBLE;
            case PURGE_ELIGIBLE -> {
                reasons.add("SKILL_LIFECYCLE_ALREADY_PURGE_ELIGIBLE");
                yield SkillRetentionState.PURGE_ELIGIBLE;
            }
        };
    }

    private List<SkillLineageEdge> lineage(
            SkillLifecycleProposal proposal,
            Instant decidedAt) {
        SkillLineageRelation relation = relation(proposal.operation());
        if (relation == null) return List.of();
        List<SkillLineageEdge> edges = new ArrayList<>();
        if (proposal.operation() == SkillLifecycleOperation.CREATE) {
            edges.add(new SkillLineageEdge(
                    proposal.proposalId(), "__ROOT__", proposal.targetSkillIds().get(0),
                    relation, decidedAt));
            return List.copyOf(edges);
        }
        for (SkillLifecycleSubject source : proposal.sources()) {
            for (String target : proposal.targetSkillIds()) {
                edges.add(new SkillLineageEdge(
                        proposal.proposalId(), source.reference(), target, relation, decidedAt));
            }
        }
        return edges.stream()
                .sorted(java.util.Comparator.comparing(SkillLineageEdge::sourceReference)
                        .thenComparing(SkillLineageEdge::targetSkillId))
                .toList();
    }

    private SkillLineageRelation relation(SkillLifecycleOperation operation) {
        return switch (operation) {
            case CREATE -> SkillLineageRelation.CREATED_AS;
            case PATCH -> SkillLineageRelation.PATCHED_FROM;
            case MERGE -> SkillLineageRelation.MERGED_FROM;
            case SPLIT -> SkillLineageRelation.SPLIT_FROM;
            case COMPRESS -> SkillLineageRelation.COMPRESSED_FROM;
            case REVIVE -> SkillLineageRelation.REVIVED_FROM;
            case ROLLBACK -> SkillLineageRelation.ROLLED_BACK_FROM;
            case DEPRECATE, PRUNE -> null;
        };
    }

    private boolean requiresReplay(SkillLifecycleOperation operation) {
        return operation == SkillLifecycleOperation.PATCH
                || operation == SkillLifecycleOperation.MERGE
                || operation == SkillLifecycleOperation.SPLIT
                || operation == SkillLifecycleOperation.COMPRESS
                || operation == SkillLifecycleOperation.PRUNE
                || operation == SkillLifecycleOperation.REVIVE
                || operation == SkillLifecycleOperation.ROLLBACK;
    }

    private void require(boolean condition, String reason, List<String> reasons) {
        if (!condition) reasons.add(reason);
    }
}
