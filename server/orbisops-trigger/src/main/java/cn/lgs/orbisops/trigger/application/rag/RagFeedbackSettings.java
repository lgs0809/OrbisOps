package cn.lgs.orbisops.trigger.application.rag;

/** Typed persistence bootstrap settings for RAG feedback and knowledge gaps. */
public record RagFeedbackSettings(boolean autoInit) {

    public static RagFeedbackSettings defaults() {
        return new RagFeedbackSettings(true);
    }
}
