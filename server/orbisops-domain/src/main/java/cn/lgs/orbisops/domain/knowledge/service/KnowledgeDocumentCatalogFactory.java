package cn.lgs.orbisops.domain.knowledge.service;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeDocumentCatalogFactory {

    public KnowledgeDocumentCatalogEntry submitted(KnowledgeBaseCatalogKey key,
                                                    String fileName,
                                                    long fileSize,
                                                    String jobId,
                                                    String jobStatus) {
        String normalizedFileName = fileName(fileName);
        return new KnowledgeDocumentCatalogEntry(
                documentId(key, normalizedFileName),
                key,
                normalizedFileName,
                normalizedFileName,
                "UPLOAD",
                extension(normalizedFileName),
                Math.max(0L, fileSize),
                "SUBMITTED",
                "ASYNC",
                0,
                true,
                value(jobId),
                Map.of(
                        "jobStatus", value(jobStatus),
                        "structurePreserved", true,
                        "segmentationMode", "STRUCTURE_FIRST"));
    }

    public KnowledgeDocumentCatalogEntry parsedDocument(KnowledgeBaseCatalogKey key,
                                                        String fileName,
                                                        String displayName,
                                                        String documentType,
                                                        long fileSize,
                                                        int chunkCount) {
        String normalizedFileName = fileName(fileName);
        return new KnowledgeDocumentCatalogEntry(
                documentId(key, normalizedFileName),
                key,
                normalizedFileName,
                text(displayName, normalizedFileName),
                "STRUCTURED_RAG",
                value(documentType),
                Math.max(0L, fileSize),
                "READY",
                "VECTOR_PIPELINE",
                Math.max(0, chunkCount),
                true,
                "",
                Map.of(
                        "syncedFrom", "RagDocumentResponseDTO",
                        "structurePreserved", true));
    }

    public KnowledgeChunkCatalogEntry parsedChunk(KnowledgeBaseCatalogKey key,
                                                  String documentId,
                                                  String chunkId,
                                                  int chunkIndex,
                                                  String chunkStrategy,
                                                  String content,
                                                  String parseStatus,
                                                  String vectorStatus,
                                                  String source,
                                                  boolean previewable) {
        String effectiveChunkId = text(chunkId, documentId + ":" + Math.max(0, chunkIndex));
        return new KnowledgeChunkCatalogEntry(
                effectiveChunkId,
                documentId,
                key,
                Math.max(0, chunkIndex),
                value(chunkStrategy),
                preview(content),
                text(parseStatus, "READY"),
                text(vectorStatus, "VECTOR_PIPELINE"),
                Map.of(
                        "source", value(source),
                        "previewable", previewable,
                        "structurePreserved", true));
    }

    public String documentId(KnowledgeBaseCatalogKey key, String fileName) {
        if (key == null) throw new IllegalArgumentException("KNOWLEDGE_BASE_KEY_REQUIRED");
        String normalizedFileName = fileName(fileName);
        return "doc-" + UUID.nameUUIDFromBytes(
                (key.scope().name() + ":" + key.projectId() + ":" + key.kbId() + ":" + normalizedFileName)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private String fileName(String input) {
        return text(input, "unnamed");
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 && index + 1 < fileName.length()
                ? fileName.substring(index + 1).toLowerCase()
                : "";
    }

    private String preview(String input) {
        String normalized = value(input);
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private String text(String input, String fallback) {
        String normalized = value(input);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
