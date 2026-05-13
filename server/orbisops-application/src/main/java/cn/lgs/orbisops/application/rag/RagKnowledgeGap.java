package cn.lgs.orbisops.application.rag;

/** Typed application view of one persisted RAG knowledge gap. */
public record RagKnowledgeGap(
        Long id,
        String gapKey,
        String queryText,
        String knowledgeTag,
        String status,
        int feedbackCount,
        String sampleComment,
        String lastFeedbackAt,
        String createTime,
        String updateTime) {

    public RagKnowledgeGap {
        gapKey = text(gapKey);
        queryText = text(queryText);
        knowledgeTag = text(knowledgeTag);
        status = text(status);
        feedbackCount = Math.max(0, feedbackCount);
        sampleComment = text(sampleComment);
        lastFeedbackAt = text(lastFeedbackAt);
        createTime = text(createTime);
        updateTime = text(updateTime);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
