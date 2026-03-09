package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;

public final class KnowledgeBaseCatalogCommands {

    private KnowledgeBaseCatalogCommands() {
    }

    public record Field<T>(boolean supplied, T value) {
        public static <T> Field<T> absent() {
            return new Field<>(false, null);
        }

        public static <T> Field<T> supplied(T value) {
            return new Field<>(true, value);
        }

        public T orElse(T fallback) {
            return supplied ? value : fallback;
        }
    }

    public record Mutation(
            Field<String> knowledgeBaseId,
            Field<String> name,
            Field<String> description,
            Field<KnowledgeStatus> status,
            Field<Long> documentCount,
            Field<Long> chunkCount,
            Field<String> sourceType,
            Field<String> retrievalPolicyJson,
            String actor) {
        public Mutation {
            knowledgeBaseId = field(knowledgeBaseId);
            name = field(name);
            description = field(description);
            status = field(status);
            documentCount = field(documentCount);
            chunkCount = field(chunkCount);
            sourceType = field(sourceType);
            retrievalPolicyJson = field(retrievalPolicyJson);
            actor = required(actor, "KNOWLEDGE_ACTOR_REQUIRED");
        }
    }

    public record StatusChange(KnowledgeStatus status, String actor) {
        public StatusChange {
            if (status == null) throw new IllegalArgumentException("KNOWLEDGE_STATUS_REQUIRED");
            actor = required(actor, "KNOWLEDGE_ACTOR_REQUIRED");
        }
    }

    private static <T> Field<T> field(Field<T> value) {
        return value == null ? Field.absent() : value;
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
