package cn.lgs.orbisops.domain.skill.model;

import java.time.Instant;

public record SkillLineageEdge(
        String proposalId,
        String sourceReference,
        String targetSkillId,
        SkillLineageRelation relation,
        Instant createdAt
) {

    public SkillLineageEdge {
        proposalId = required(proposalId, "SKILL_LINEAGE_PROPOSAL_ID_REQUIRED");
        sourceReference = required(sourceReference, "SKILL_LINEAGE_SOURCE_REQUIRED");
        targetSkillId = required(targetSkillId, "SKILL_LINEAGE_TARGET_REQUIRED");
        if (relation == null) throw new IllegalArgumentException("SKILL_LINEAGE_RELATION_REQUIRED");
        if (createdAt == null) throw new IllegalArgumentException("SKILL_LINEAGE_TIME_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
