package cn.lgs.orbisops.domain.skill.model;

import java.util.List;

public record SkillLifecycleDecision(
        String proposalId,
        boolean approved,
        SkillRetentionState targetRetentionState,
        List<SkillLineageEdge> lineageEdges,
        List<String> reasonCodes
) {

    public SkillLifecycleDecision {
        proposalId = required(proposalId, "SKILL_LIFECYCLE_DECISION_ID_REQUIRED");
        lineageEdges = lineageEdges == null ? List.of() : List.copyOf(lineageEdges);
        reasonCodes = reasonCodes == null ? List.of() : reasonCodes.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank()).distinct().sorted().toList();
        if (approved && !reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("SKILL_LIFECYCLE_APPROVED_WITH_REASONS");
        }
        if (!approved && reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("SKILL_LIFECYCLE_REJECTION_REASON_REQUIRED");
        }
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
