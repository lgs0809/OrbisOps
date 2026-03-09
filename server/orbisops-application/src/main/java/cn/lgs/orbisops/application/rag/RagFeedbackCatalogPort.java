package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Application port for RAG feedback and knowledge-gap persistence. */
public interface RagFeedbackCatalogPort {

    void ensureReady();

    Long insertFeedback(RagFeedbackSubmitCommand command);

    RagKnowledgeGap upsertGap(String query, String knowledgeTag, String comment);

    List<RagFeedbackEntry> listFeedback(String knowledgeTag, Boolean useful, Boolean resolved, int limit);

    List<RagKnowledgeGap> listGaps(String status, String knowledgeTag, int limit);

    boolean updateGapStatus(Long id, String status);

    RagKnowledgeGap findGap(Long id);
}
