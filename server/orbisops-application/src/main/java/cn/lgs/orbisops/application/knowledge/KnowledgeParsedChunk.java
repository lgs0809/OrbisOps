package cn.lgs.orbisops.application.knowledge;

public record KnowledgeParsedChunk(
        String chunkId,
        String fileName,
        String displayName,
        String source,
        String documentType,
        int chunkIndex,
        String chunkStrategy,
        long size,
        String content,
        String parseStatus,
        String vectorStatus,
        boolean previewable
) {

    public KnowledgeParsedChunk {
        chunkId = value(chunkId);
        fileName = text(fileName, "unknown");
        displayName = text(displayName, fileName);
        source = value(source);
        documentType = value(documentType);
        chunkIndex = Math.max(0, chunkIndex);
        chunkStrategy = value(chunkStrategy);
        size = Math.max(0L, size);
        content = value(content);
        parseStatus = text(parseStatus, "READY");
        vectorStatus = text(vectorStatus, "VECTOR_PIPELINE");
    }

    private static String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
