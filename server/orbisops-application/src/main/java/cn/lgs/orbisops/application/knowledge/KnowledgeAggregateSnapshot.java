package cn.lgs.orbisops.application.knowledge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KnowledgeAggregateSnapshot(
        String knowledgeBaseId,
        long documentCount,
        long chunkCount,
        List<Map<String, Object>> ragOrders) {

    public KnowledgeAggregateSnapshot {
        knowledgeBaseId = required(knowledgeBaseId);
        if (documentCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_COUNT_INVALID");
        if (chunkCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_CHUNK_COUNT_INVALID");
        ragOrders = ragOrders == null
                ? List.of()
                : ragOrders.stream()
                .map(item -> item == null
                        ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(item)))
                .toList();
    }

    private static String required(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_BASE_ID_REQUIRED");
        return normalized;
    }
}
