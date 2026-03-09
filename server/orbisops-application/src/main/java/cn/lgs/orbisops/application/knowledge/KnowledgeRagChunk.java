package cn.lgs.orbisops.application.knowledge;

public record KnowledgeRagChunk(
        String chunkId,
        String knowledgeTag,
        String fileName,
        String displayName,
        String source,
        String documentType,
        int chunkIndex,
        String chunkStrategy,
        long size,
        String content,
        boolean previewable,
        String updatedAt
) {

    public KnowledgeRagChunk {
        chunkId = value(chunkId);
        knowledgeTag = value(knowledgeTag);
        fileName = text(fileName, source);
        displayName = text(displayName, fileName);
        source = value(source);
        documentType = value(documentType);
        chunkIndex = Math.max(0, chunkIndex);
        chunkStrategy = value(chunkStrategy);
        size = Math.max(0L, size);
        content = value(content);
        updatedAt = value(updatedAt);
    }

    private static String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? value(fallback) : normalized;
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
