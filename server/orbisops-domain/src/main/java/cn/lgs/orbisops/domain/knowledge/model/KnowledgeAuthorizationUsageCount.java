package cn.lgs.orbisops.domain.knowledge.model;

public record KnowledgeAuthorizationUsageCount(
        String globalKbId,
        long projectCount
) {

    public KnowledgeAuthorizationUsageCount {
        globalKbId = globalKbId == null ? "" : globalKbId.trim();
        if (globalKbId.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_BASE_ID_REQUIRED");
        if (projectCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_USAGE_COUNT_INVALID");
    }
}
