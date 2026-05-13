package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record KnowledgeBaseCatalogSnapshot(
        Long id,
        String knowledgeBaseId,
        String name,
        KnowledgeScope scope,
        String projectId,
        String description,
        KnowledgeStatus status,
        long documentCount,
        long chunkCount,
        String sourceType,
        String retrievalPolicyJson,
        String createBy,
        String createdAt,
        String updatedAt,
        List<Map<String, Object>> ragOrders) {

    public KnowledgeBaseCatalogSnapshot {
        knowledgeBaseId = required(knowledgeBaseId, "KNOWLEDGE_BASE_ID_REQUIRED");
        name = normalized(name, knowledgeBaseId);
        if (scope == null) throw new IllegalArgumentException("KNOWLEDGE_SCOPE_REQUIRED");
        projectId = normalized(projectId, "");
        if (scope == KnowledgeScope.PROJECT && projectId.isBlank()) {
            throw new IllegalArgumentException("KNOWLEDGE_PROJECT_ID_REQUIRED");
        }
        if (scope == KnowledgeScope.GLOBAL) projectId = "";
        description = normalized(description, "");
        status = status == null ? KnowledgeStatus.ENABLED : status;
        if (documentCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_DOCUMENT_COUNT_INVALID");
        if (chunkCount < 0L) throw new IllegalArgumentException("KNOWLEDGE_CHUNK_COUNT_INVALID");
        sourceType = normalized(sourceType, "DB");
        retrievalPolicyJson = normalized(retrievalPolicyJson, "");
        createBy = normalized(createBy, "");
        createdAt = normalized(createdAt, "");
        updatedAt = normalized(updatedAt, "");
        ragOrders = ragOrders == null
                ? List.of()
                : ragOrders.stream()
                .map(item -> item == null
                        ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(item)))
                .toList();
    }

    public static KnowledgeBaseCatalogSnapshot from(KnowledgeBaseCatalogEntry entry) {
        if (entry == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_REQUIRED");
        return new KnowledgeBaseCatalogSnapshot(
                entry.id(),
                entry.key().kbId(),
                entry.name(),
                entry.key().scope(),
                entry.key().projectId(),
                entry.description(),
                entry.status(),
                entry.documentCount(),
                entry.chunkCount(),
                entry.sourceType(),
                entry.retrievalPolicyJson(),
                entry.createBy(),
                entry.createdAt(),
                entry.updatedAt(),
                List.of());
    }

    public static KnowledgeBaseCatalogSnapshot from(KnowledgeAggregateSnapshot aggregate) {
        if (aggregate == null) throw new IllegalArgumentException("KNOWLEDGE_AGGREGATE_REQUIRED");
        return new KnowledgeBaseCatalogSnapshot(
                null,
                aggregate.knowledgeBaseId(),
                aggregate.knowledgeBaseId(),
                KnowledgeScope.GLOBAL,
                "",
                "由现有 RAG 文档聚合得到的通用知识标签",
                KnowledgeStatus.ENABLED,
                aggregate.documentCount(),
                aggregate.chunkCount(),
                "VECTOR_AGGREGATE",
                "",
                "",
                "",
                "",
                aggregate.ragOrders());
    }

    public KnowledgeBaseCatalogSnapshot withAggregateCounts(KnowledgeAggregateSnapshot aggregate) {
        if (aggregate == null || !knowledgeBaseId.equals(aggregate.knowledgeBaseId())) return this;
        return new KnowledgeBaseCatalogSnapshot(
                id, knowledgeBaseId, name, scope, projectId, description, status,
                aggregate.documentCount(), aggregate.chunkCount(), sourceType,
                retrievalPolicyJson, createBy, createdAt, updatedAt, ragOrders);
    }

    public Map<String, Object> view(boolean includePolicy) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("kbId", knowledgeBaseId);
        data.put("knowledgeTag", knowledgeBaseId);
        data.put("ragId", knowledgeBaseId);
        data.put("name", name);
        data.put("kbName", name);
        data.put("ragName", name);
        data.put("projectId", projectId);
        data.put("scope", scope.name());
        data.put("description", description);
        data.put("status", status.name());
        data.put("statusCode", status == KnowledgeStatus.ENABLED ? 1 : 0);
        data.put("documentCount", documentCount);
        data.put("chunkCount", chunkCount);
        data.put("sourceType", sourceType);
        data.put("createBy", createBy);
        data.put("createTime", createdAt);
        data.put("updateTime", updatedAt);
        if (!ragOrders.isEmpty()) data.put("ragOrders", ragOrders);
        if (includePolicy) data.put("retrievalPolicyJson", retrievalPolicyJson);
        return Collections.unmodifiableMap(data);
    }

    private static String required(String value, String reasonCode) {
        String normalized = normalized(value, "");
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private static String normalized(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
