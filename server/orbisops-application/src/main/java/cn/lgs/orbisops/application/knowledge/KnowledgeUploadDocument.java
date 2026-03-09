package cn.lgs.orbisops.application.knowledge;

public record KnowledgeUploadDocument(
        String fileName,
        long fileSize
) {

    public KnowledgeUploadDocument {
        fileName = fileName == null || fileName.trim().isBlank() ? "unnamed" : fileName.trim();
        fileSize = Math.max(0L, fileSize);
    }
}
