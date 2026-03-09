package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Typed command for one RAG answer feedback submission. */
public record RagFeedbackSubmitCommand(
        String query,
        String answer,
        Boolean useful,
        Boolean resolved,
        String sourceType,
        String sourceId,
        String knowledgeTag,
        List<String> chunkIds,
        String comment) {

    public RagFeedbackSubmitCommand {
        query = text(query);
        if (query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        answer = text(answer);
        sourceType = text(sourceType);
        sourceId = text(sourceId);
        knowledgeTag = text(knowledgeTag);
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
        comment = text(comment);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
