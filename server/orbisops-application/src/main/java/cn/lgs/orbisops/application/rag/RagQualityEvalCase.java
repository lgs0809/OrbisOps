package cn.lgs.orbisops.application.rag;

import java.util.LinkedHashSet;
import java.util.List;

/** One typed RAG quality evaluation case. */
public record RagQualityEvalCase(
        String caseName,
        String query,
        String knowledgeTag,
        List<String> expectedKeywords,
        int topK) {

    public RagQualityEvalCase {
        query = text(query);
        if (query.isBlank()) throw new IllegalArgumentException("query 不能为空");
        caseName = text(caseName);
        if (caseName.isBlank()) caseName = query;
        knowledgeTag = text(knowledgeTag);
        expectedKeywords = expectedKeywords == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(expectedKeywords.stream()
                .map(RagQualityEvalCase::text)
                .filter(value -> !value.isBlank())
                .toList()));
        topK = Math.max(1, Math.min(topK <= 0 ? 8 : topK, 30));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
