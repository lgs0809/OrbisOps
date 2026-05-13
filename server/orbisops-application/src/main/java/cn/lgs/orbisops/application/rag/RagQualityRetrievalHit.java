package cn.lgs.orbisops.application.rag;

/** Typed retrieval hit returned by the online RAG retrieval adapter. */
public record RagQualityRetrievalHit<M>(
        String chunkId,
        String docName,
        String knowledgeTag,
        String documentType,
        String chunkStrategy,
        String content,
        M metadata) {

    public RagQualityRetrievalHit {
        chunkId = text(chunkId);
        docName = text(docName);
        knowledgeTag = text(knowledgeTag);
        documentType = text(documentType);
        chunkStrategy = text(chunkStrategy);
        content = content == null ? "" : content;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
