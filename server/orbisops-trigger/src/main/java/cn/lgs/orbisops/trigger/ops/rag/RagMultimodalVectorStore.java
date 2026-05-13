package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Typed vector persistence and query projection over the multimodal repository. */
public final class RagMultimodalVectorStore {

    private final IRagMultimodalRepository repository;
    private final RagMultimodalSettings settings;

    public RagMultimodalVectorStore(
            IRagMultimodalRepository repository,
            RagMultimodalSettings settings) {
        if (repository == null) throw new IllegalArgumentException("RAG_MULTIMODAL_REPOSITORY_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        this.repository = repository;
        this.settings = settings;
    }

    public void upsert(
            String id,
            String content,
            Map<String, Object> metadata,
            List<Double> embedding) {
        validateWriteDimension(embedding);
        repository.upsert(
                settings.tableName(),
                settings.dimension(),
                id,
                content,
                metadata,
                vectorLiteral(embedding));
    }

    public List<RagDocument> search(
            List<Double> embedding,
            String filterExpression,
            int requestedTopK) {
        return repository.search(
                settings.tableName(),
                vectorLiteral(embedding),
                settings.dimension(),
                filterExpression,
                settings.resolveSearchTopK(requestedTopK),
                settings.provider(),
                settings.model());
    }

    String vectorLiteral(List<Double> embedding) {
        if (embedding == null) {
            return "[]";
        }
        return embedding.stream()
                .map(value -> String.format(Locale.ROOT, "%.10f", value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private void validateWriteDimension(List<Double> embedding) {
        if (embedding == null || embedding.size() != settings.dimension()) {
            throw new IllegalStateException("Embedding dimension mismatch, expected=" + settings.dimension()
                    + ", actual=" + (embedding == null ? 0 : embedding.size()));
        }
    }
}
