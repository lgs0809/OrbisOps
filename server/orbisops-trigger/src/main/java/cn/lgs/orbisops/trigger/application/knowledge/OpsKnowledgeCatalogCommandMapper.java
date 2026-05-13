package cn.lgs.orbisops.trigger.application.knowledge;

import cn.lgs.orbisops.application.knowledge.KnowledgeBaseCatalogCommands;
import cn.lgs.orbisops.application.knowledge.KnowledgeRetrievalPolicyCommand;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class OpsKnowledgeCatalogCommandMapper {

    public KnowledgeBaseCatalogCommands.Mutation mutation(Map<String, Object> request,
                                                           String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new KnowledgeBaseCatalogCommands.Mutation(
                aliasTextField(safe, "kbId", "knowledgeTag"),
                aliasTextField(safe, "name", "kbName", "ragName"),
                textField(safe, "description"),
                enumField(safe, "status", KnowledgeStatus::require),
                longField(safe, "documentCount"),
                longField(safe, "chunkCount"),
                textField(safe, "sourceType"),
                aliasTextField(safe, "retrievalPolicyJson", "retrievalPolicy"),
                actor);
    }

    public KnowledgeBaseCatalogCommands.StatusChange status(Map<String, Object> request,
                                                             String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        KnowledgeStatus status = KnowledgeStatus.require(text(safe.getOrDefault("status", "DISABLED")));
        return new KnowledgeBaseCatalogCommands.StatusChange(status, actor);
    }

    public KnowledgeRetrievalPolicyCommand retrievalPolicy(Map<String, Object> request,
                                                            String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new KnowledgeRetrievalPolicyCommand(
                aliasInteger(safe, "maxSegmentChars", "chunkSize"),
                aliasInteger(safe, "hardSplitOverlapChars", "overlapSize"),
                integer(safe.get("topK")),
                bool(safe.get("rerankEnabled")),
                text(safe.get("embeddingModelId")),
                firstText(safe.get("metadataFilterJson"), safe.get("metadataFilter")),
                actor);
    }

    private KnowledgeBaseCatalogCommands.Field<String> textField(Map<String, Object> source,
                                                                  String key) {
        return source.containsKey(key)
                ? KnowledgeBaseCatalogCommands.Field.supplied(text(source.get(key)))
                : KnowledgeBaseCatalogCommands.Field.absent();
    }

    private KnowledgeBaseCatalogCommands.Field<String> aliasTextField(Map<String, Object> source,
                                                                       String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) return textField(source, key);
        }
        return KnowledgeBaseCatalogCommands.Field.absent();
    }

    private <T> KnowledgeBaseCatalogCommands.Field<T> enumField(
            Map<String, Object> source,
            String key,
            java.util.function.Function<String, T> parser) {
        return source.containsKey(key)
                ? KnowledgeBaseCatalogCommands.Field.supplied(parser.apply(text(source.get(key))))
                : KnowledgeBaseCatalogCommands.Field.absent();
    }

    private KnowledgeBaseCatalogCommands.Field<Long> longField(Map<String, Object> source,
                                                                String key) {
        return source.containsKey(key)
                ? KnowledgeBaseCatalogCommands.Field.supplied(longValue(source.get(key)))
                : KnowledgeBaseCatalogCommands.Field.absent();
    }

    private Integer aliasInteger(Map<String, Object> source, String primary, String alias) {
        if (source.containsKey(primary)) return integer(source.get(primary));
        return source.containsKey(alias) ? integer(source.get(alias)) : null;
    }

    private Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(text(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.valueOf(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Boolean bool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String normalized = text(value);
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
