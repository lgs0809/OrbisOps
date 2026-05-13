package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.api.dto.RagDocumentResponseDTO;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagDocumentApplicationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Compatibility facade for the legacy admin RAG document endpoints. */
@Service
public class LegacyRagDocumentApplicationService {

    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final KnowledgeRagDocumentApplicationService documentService;

    public LegacyRagDocumentApplicationService(
            KnowledgeRagDocumentApplicationService documentService) {
        if (documentService == null) {
            throw new IllegalArgumentException("KNOWLEDGE_RAG_DOCUMENT_SERVICE_REQUIRED");
        }
        this.documentService = documentService;
    }

    public Map<String, Object> documentStats(String tag) {
        return documentService.statistics(GLOBAL_SCOPE, "", value(tag));
    }

    public boolean deleteChunk(String chunkId) {
        return documentService.deleteChunk(chunkId);
    }

    public Map<String, Object> deleteChunksByTag(String tag) {
        return documentService.deleteChunks(GLOBAL_SCOPE, "", value(tag));
    }

    public List<RagDocumentResponseDTO> listDocuments(String tag) {
        return documentService.list(GLOBAL_SCOPE, "", value(tag), 500).stream()
                .map(view -> response(view, false))
                .toList();
    }

    public RagDocumentResponseDTO documentContent(String fileName) {
        Map<String, Object> view = documentService.content(fileName);
        return view == null || view.isEmpty() ? null : response(view, true);
    }

    private RagDocumentResponseDTO response(
            Map<String, Object> view,
            boolean includeContent) {
        String fileName = text(view.get("fileName"), text(view.get("documentId"), ""));
        return RagDocumentResponseDTO.builder()
                .fileName(fileName)
                .displayName(text(view.get("displayName"), fileName))
                .tag(text(view.get("tag"), text(view.get("knowledgeTag"), "default")))
                .size(longValue(view.get("size")))
                .updateTime(null)
                .previewable(Boolean.TRUE.equals(view.get("previewable")))
                .content(includeContent ? text(view.get("content"), "") : null)
                .chunkId(text(view.get("chunkId"), fileName))
                .chunkIndex(integer(view.get("chunkIndex")))
                .source(text(view.get("source"), ""))
                .documentType(text(view.get("documentType"), ""))
                .chunkStrategy(text(view.get("chunkStrategy"), ""))
                .build();
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
