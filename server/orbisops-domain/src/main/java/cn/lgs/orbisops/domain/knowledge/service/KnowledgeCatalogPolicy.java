package cn.lgs.orbisops.domain.knowledge.service;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeRetrievalPolicy;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;

import java.util.Locale;

public final class KnowledgeCatalogPolicy {

    private static final int DEFAULT_MAX_SEGMENT_CHARS = 3000;
    private static final int DEFAULT_OVERLAP_CHARS = 0;
    private static final int DEFAULT_TOP_K = 5;

    public KnowledgeBaseCatalogEntry create(KnowledgeBaseCatalogEntry candidate) {
        if (candidate == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_REQUIRED");
        requiredId(candidate.key().kbId());
        return candidate;
    }

    public KnowledgeBaseCatalogEntry update(KnowledgeBaseCatalogEntry current,
                                            KnowledgeBaseCatalogEntry candidate) {
        if (current == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_CURRENT_REQUIRED");
        if (candidate == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_REQUIRED");
        if (!current.key().equals(candidate.key())) {
            throw new IllegalArgumentException("KNOWLEDGE_BASE_ID_IMMUTABLE");
        }
        requiredId(candidate.key().kbId());
        return candidate;
    }

    public KnowledgeStatus status(KnowledgeStatus target) {
        if (target == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_REQUIRED");
        return target;
    }

    public ProjectKnowledgeAuthorization authorizeGlobal(String projectId,
                                                          String globalKbId,
                                                          KnowledgeStatus status,
                                                          String enabledBy) {
        return new ProjectKnowledgeAuthorization(
                requiredProject(projectId),
                requiredId(globalKbId),
                status == null ? KnowledgeStatus.ENABLED : status,
                enabledBy,
                "",
                "");
    }

    public KnowledgeRetrievalPolicy retrievalPolicy(Integer maxSegmentChars,
                                                     Integer hardSplitOverlapChars,
                                                     Integer topK,
                                                     Boolean rerankEnabled,
                                                     String embeddingModelId,
                                                     String metadataFilterJson) {
        int segment = bounded(maxSegmentChars, DEFAULT_MAX_SEGMENT_CHARS, 1000, 12000);
        int overlap = bounded(hardSplitOverlapChars, DEFAULT_OVERLAP_CHARS, 0, segment / 2);
        int resolvedTopK = bounded(topK, DEFAULT_TOP_K, 1, 50);
        String filter = value(metadataFilterJson);
        if (filter.isBlank()) filter = "{}";
        return new KnowledgeRetrievalPolicy(
                segment,
                overlap,
                resolvedTopK,
                Boolean.TRUE.equals(rerankEnabled),
                value(embeddingModelId),
                filter);
    }

    public KnowledgeRetrievalPolicy defaultRetrievalPolicy() {
        return retrievalPolicy(null, null, null, null, "", "{}");
    }

    public String requiredId(String value) {
        String normalized = normalizedId(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_BASE_ID_REQUIRED");
        return normalized;
    }

    public String requiredProject(String value) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_PROJECT_ID_REQUIRED");
        return normalized;
    }

    public String requiredChunk(String value) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("KNOWLEDGE_CHUNK_ID_REQUIRED");
        return normalized;
    }

    private int bounded(Integer value, int fallback, int min, int max) {
        int parsed = value == null ? fallback : value;
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("KNOWLEDGE_RETRIEVAL_PARAMETER_INVALID:" + parsed);
        }
        return parsed;
    }

    private String normalizedId(Object input) {
        String normalized = value(input).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "-");
        return normalized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
    }

    private String value(Object input) {
        return input == null ? "" : String.valueOf(input).trim();
    }
}
