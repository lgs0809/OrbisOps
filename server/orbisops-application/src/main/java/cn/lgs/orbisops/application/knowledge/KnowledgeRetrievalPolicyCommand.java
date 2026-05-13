package cn.lgs.orbisops.application.knowledge;

public record KnowledgeRetrievalPolicyCommand(
        Integer maxSegmentChars,
        Integer hardSplitOverlapChars,
        Integer topK,
        Boolean rerankEnabled,
        String embeddingModelId,
        String metadataFilterJson,
        String actor) {

    public KnowledgeRetrievalPolicyCommand {
        embeddingModelId = normalize(embeddingModelId);
        metadataFilterJson = normalize(metadataFilterJson);
        actor = required(actor, "KNOWLEDGE_ACTOR_REQUIRED");
    }

    private static String required(String value, String reasonCode) {
        String normalized = normalize(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
