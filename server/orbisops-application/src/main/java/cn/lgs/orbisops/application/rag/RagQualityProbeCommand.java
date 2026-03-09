package cn.lgs.orbisops.application.rag;

import java.util.LinkedHashSet;
import java.util.List;

/** Typed request for one online RAG quality probe. */
public record RagQualityProbeCommand(
        String query,
        String knowledgeTag,
        List<String> expectedKeywords,
        int topK,
        String retrievalMode,
        boolean rerankEnabled) {

    public RagQualityProbeCommand {
        query = text(query);
        if (query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        knowledgeTag = text(knowledgeTag);
        expectedKeywords = expectedKeywords == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(expectedKeywords.stream()
                .map(RagQualityProbeCommand::text)
                .filter(value -> !value.isBlank())
                .toList()));
        topK = Math.max(1, Math.min(topK <= 0 ? 8 : topK, 30));
        retrievalMode = text(retrievalMode);
        if (retrievalMode.isBlank()) retrievalMode = "hybrid";
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
