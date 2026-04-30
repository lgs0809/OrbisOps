package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;
import java.util.List;

public record SkillLifecycleProposal(
        String proposalId,
        SkillLifecycleOperation operation,
        List<SkillLifecycleSubject> sources,
        List<String> targetSkillIds,
        String reason,
        List<String> evidenceIds,
        String behaviorEvaluationId,
        boolean behaviorReplayPassed,
        Instant proposedAt
) {

    public SkillLifecycleProposal {
        proposalId = required(proposalId, "SKILL_LIFECYCLE_PROPOSAL_ID_REQUIRED");
        if (operation == null) throw new IllegalArgumentException("SKILL_LIFECYCLE_OPERATION_REQUIRED");
        sources = sources == null ? List.of() : List.copyOf(sources);
        targetSkillIds = targetSkillIds == null ? List.of() : targetSkillIds.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        reason = required(reason, "SKILL_LIFECYCLE_REASON_REQUIRED");
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        behaviorEvaluationId = behaviorEvaluationId == null ? "" : behaviorEvaluationId.trim();
        if (proposedAt == null) throw new IllegalArgumentException("SKILL_LIFECYCLE_TIME_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
