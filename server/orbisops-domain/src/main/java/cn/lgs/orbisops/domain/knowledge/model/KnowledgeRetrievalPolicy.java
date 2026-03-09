package cn.lgs.orbisops.domain.knowledge.model;

import java.util.Map;

public record KnowledgeRetrievalPolicy(int maxSegmentChars,
                                       int hardSplitOverlapChars,
                                       int topK,
                                       boolean rerankEnabled,
                                       String embeddingModelId,
                                       String metadataFilterJson) {

    public KnowledgeRetrievalPolicy {
        if (maxSegmentChars < 1000 || maxSegmentChars > 12000) {
            throw new IllegalArgumentException("KNOWLEDGE_SEGMENT_SIZE_INVALID:" + maxSegmentChars);
        }
        if (hardSplitOverlapChars < 0 || hardSplitOverlapChars > maxSegmentChars / 2) {
            throw new IllegalArgumentException("KNOWLEDGE_OVERLAP_INVALID:" + hardSplitOverlapChars);
        }
        if (topK < 1 || topK > 50) {
            throw new IllegalArgumentException("KNOWLEDGE_TOP_K_INVALID:" + topK);
        }
        embeddingModelId = value(embeddingModelId);
        metadataFilterJson = value(metadataFilterJson);
        if (metadataFilterJson.isBlank()) metadataFilterJson = "{}";
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "maxSegmentChars", maxSegmentChars,
                "chunkSize", maxSegmentChars,
                "hardSplitOverlapChars", hardSplitOverlapChars,
                "overlapSize", hardSplitOverlapChars,
                "topK", topK,
                "rerankEnabled", rerankEnabled,
                "embeddingModelId", embeddingModelId,
                "metadataFilterJson", metadataFilterJson);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
