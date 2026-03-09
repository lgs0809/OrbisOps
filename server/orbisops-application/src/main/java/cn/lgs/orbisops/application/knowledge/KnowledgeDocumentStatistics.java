package cn.lgs.orbisops.application.knowledge;

import java.util.List;

public record KnowledgeDocumentStatistics(
        long chunkCount,
        long documentCount,
        List<KnowledgeCountBreakdown> byType,
        List<KnowledgeCountBreakdown> bySource,
        String knowledgeTag,
        String scope,
        String projectId
) {

    public KnowledgeDocumentStatistics {
        chunkCount = Math.max(0L, chunkCount);
        documentCount = Math.max(0L, documentCount);
        byType = byType == null ? List.of() : List.copyOf(byType);
        bySource = bySource == null ? List.of() : List.copyOf(bySource);
        knowledgeTag = value(knowledgeTag);
        scope = value(scope);
        projectId = value(projectId);
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
