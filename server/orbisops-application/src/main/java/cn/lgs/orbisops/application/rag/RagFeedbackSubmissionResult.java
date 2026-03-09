package cn.lgs.orbisops.application.rag;

/** Result of one feedback submission and its optional knowledge-gap projection. */
public record RagFeedbackSubmissionResult(
        Long id,
        boolean gapCreated,
        RagKnowledgeGap gap) {
}
