package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeChunkDeletionOutcome;
import cn.lgs.orbisops.application.knowledge.KnowledgeCountBreakdown;
import cn.lgs.orbisops.application.knowledge.KnowledgeDocumentStatistics;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagChunk;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagDocumentPort;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagKnowledgeDocumentRecord;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trigger ACL from the legacy RAG repository into the typed Knowledge document port. */
@Component
public class OpsKnowledgeRagDocumentAdapter implements KnowledgeRagDocumentPort {

    private final IRagKnowledgeRepository repository;

    public OpsKnowledgeRagDocumentAdapter(IRagKnowledgeRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("RAG_KNOWLEDGE_REPOSITORY_REQUIRED");
        }
        this.repository = repository;
    }

    @Override
    public List<KnowledgeRagChunk> list(String kbId, String scope, String projectId) {
        List<RagKnowledgeDocumentRecord> records = repository.listDocuments(kbId, scope, projectId);
        return (records == null ? List.<RagKnowledgeDocumentRecord>of() : records).stream()
                .filter(record -> record != null)
                .map(record -> chunk(record, false))
                .toList();
    }

    @Override
    public KnowledgeDocumentStatistics statistics(
            String kbId,
            String scope,
            String projectId) {
        Map<String, Object> values = repository.documentStats(kbId, scope, projectId);
        Map<String, Object> safe = values == null ? Map.of() : values;
        return new KnowledgeDocumentStatistics(
                number(safe.get("chunkCount")),
                number(safe.get("documentCount")),
                breakdowns(safe.get("byType")),
                breakdowns(safe.get("bySource")),
                text(safe.get("tag"), kbId),
                text(safe.get("scope"), scope),
                text(safe.get("projectId"), projectId));
    }

    @Override
    public boolean deleteChunk(
            String chunkId,
            String kbId,
            String scope,
            String projectId) {
        return repository.deleteChunk(chunkId, kbId, scope, projectId);
    }

    @Override
    public boolean deleteChunk(String chunkId) {
        return repository.deleteChunk(chunkId);
    }

    @Override
    public KnowledgeChunkDeletionOutcome deleteChunks(
            String kbId,
            String scope,
            String projectId) {
        Map<String, Object> values = repository.deleteChunksByTag(kbId, scope, projectId);
        Map<String, Object> safe = values == null ? Map.of() : values;
        return new KnowledgeChunkDeletionOutcome(
                text(safe.get("knowledgeTag"), kbId),
                text(safe.get("scope"), scope),
                text(safe.get("projectId"), projectId),
                number(safe.get("deletedChunks")));
    }

    @Override
    public KnowledgeRagChunk content(String chunkId) {
        RagKnowledgeDocumentRecord record = repository.documentContent(chunkId);
        return record == null ? null : chunk(record, true);
    }

    private KnowledgeRagChunk chunk(
            RagKnowledgeDocumentRecord record,
            boolean includeContent) {
        Map<String, Object> metadata = metadata(record.metadataText());
        String source = firstText(metadata, "source", "file_name", "filename", "name");
        String fileName = text(record.id(), source);
        String displayName = text(source, fileName);
        String rawContent = record.content() == null ? "" : record.content();
        return new KnowledgeRagChunk(
                fileName,
                text(metadata.get("knowledge"), "default"),
                fileName,
                displayName,
                text(source, ""),
                firstText(metadata, "document_type"),
                integer(metadata.get("chunk_index")),
                firstText(metadata, "chunk_strategy"),
                rawContent.length(),
                includeContent ? rawContent : "",
                true,
                "");
    }

    private List<KnowledgeCountBreakdown> breakdowns(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(item -> new KnowledgeCountBreakdown(
                        text(item.get("key"), "unknown"),
                        number(item.get("count"))))
                .toList();
    }

    private Map<String, Object> metadata(String metadataText) {
        if (!StringUtils.hasText(metadataText)) {
            return Map.of();
        }
        try {
            JSONObject parsed = JSON.parseObject(metadataText);
            return parsed == null ? Map.of() : new HashMap<>(parsed);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String firstText(Map<String, Object> values, String... keys) {
        return Arrays.stream(keys)
                .map(values::get)
                .map(value -> text(value, ""))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return value == null ? 0L : Math.max(0L, Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? (fallback == null ? "" : fallback.trim()) : normalized;
    }
}
