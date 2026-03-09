package cn.lgs.orbisops.trigger.ops.rag;

/** Typed global bounds applied before request-specific RAG parse policy normalization. */
public record RagDocumentParserSettings(
        int maxChunkChars,
        int maxPdfImages) {

    public RagDocumentParserSettings {
        maxChunkChars = maxChunkChars < 0 || maxChunkChars > 200_000
                ? 3_000
                : maxChunkChars;
        maxPdfImages = maxPdfImages < 0 || maxPdfImages > 1_000
                ? 20
                : maxPdfImages;
    }

    public static RagDocumentParserSettings defaults() {
        return new RagDocumentParserSettings(3_000, 20);
    }

    static RagDocumentParserSettings legacyConstructorDefaults() {
        return new RagDocumentParserSettings(0, 0);
    }
}
