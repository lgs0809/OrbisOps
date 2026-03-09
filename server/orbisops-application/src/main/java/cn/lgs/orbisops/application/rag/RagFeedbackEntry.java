package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Typed application view of one persisted RAG feedback entry. */
public record RagFeedbackEntry(
        Long id,
        String queryText,
        String answerText,
        Boolean useful,
        Boolean resolved,
        String sourceType,
        String sourceId,
        String knowledgeTag,
        List<String> chunkIds,
        String commentText,
        String createTime) {

    public RagFeedbackEntry {
        queryText = text(queryText);
        answerText = text(answerText);
        sourceType = text(sourceType);
        sourceId = text(sourceId);
        knowledgeTag = text(knowledgeTag);
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
        commentText = text(commentText);
        createTime = text(createTime);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
