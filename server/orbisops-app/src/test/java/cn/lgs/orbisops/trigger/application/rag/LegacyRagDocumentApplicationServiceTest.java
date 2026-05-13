package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.api.dto.RagDocumentResponseDTO;
import cn.lgs.orbisops.application.knowledge.KnowledgeRagDocumentApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyRagDocumentApplicationServiceTest {

    @Test
    void delegatesLegacyGlobalEndpointsToTypedKnowledgeApplicationService() {
        KnowledgeRagDocumentApplicationService documentService = mock(KnowledgeRagDocumentApplicationService.class);
        LegacyRagDocumentApplicationService service = new LegacyRagDocumentApplicationService(documentService);
        Map<String, Object> statistics = Map.of("chunkCount", 3L, "documentCount", 1L);
        Map<String, Object> deletion = Map.of("knowledgeTag", "ops", "deletedChunks", 3L);
        when(documentService.statistics("GLOBAL", "", "ops")).thenReturn(statistics);
        when(documentService.deleteChunk("chunk-1")).thenReturn(true);
        when(documentService.deleteChunks("GLOBAL", "", "ops")).thenReturn(deletion);

        assertEquals(statistics, service.documentStats(" ops "));
        assertTrue(service.deleteChunk("chunk-1"));
        assertEquals(deletion, service.deleteChunksByTag(" ops "));

        verify(documentService).statistics("GLOBAL", "", "ops");
        verify(documentService).deleteChunk("chunk-1");
        verify(documentService).deleteChunks("GLOBAL", "", "ops");
    }

    @Test
    void listOmitsContentWhileContentEndpointIncludesFullBody() {
        KnowledgeRagDocumentApplicationService documentService = mock(KnowledgeRagDocumentApplicationService.class);
        LegacyRagDocumentApplicationService service = new LegacyRagDocumentApplicationService(documentService);
        Map<String, Object> listed = Map.ofEntries(
                Map.entry("documentId", "chunk-1"),
                Map.entry("chunkId", "chunk-1"),
                Map.entry("knowledgeTag", "ops"),
                Map.entry("tag", "ops"),
                Map.entry("fileName", "chunk-1"),
                Map.entry("displayName", "runbook.md"),
                Map.entry("source", "runbook.md"),
                Map.entry("documentType", "markdown"),
                Map.entry("chunkIndex", 2),
                Map.entry("chunkStrategy", "section"),
                Map.entry("size", 12L),
                Map.entry("content", ""),
                Map.entry("previewable", true));
        Map<String, Object> content = Map.ofEntries(
                Map.entry("documentId", "chunk-1"),
                Map.entry("chunkId", "chunk-1"),
                Map.entry("knowledgeTag", "ops"),
                Map.entry("tag", "ops"),
                Map.entry("fileName", "chunk-1"),
                Map.entry("displayName", "runbook.md"),
                Map.entry("source", "runbook.md"),
                Map.entry("documentType", "markdown"),
                Map.entry("chunkIndex", 2),
                Map.entry("chunkStrategy", "section"),
                Map.entry("size", 12L),
                Map.entry("content", "full content"),
                Map.entry("previewable", true));
        when(documentService.list("GLOBAL", "", "ops", 500)).thenReturn(List.of(listed));
        when(documentService.content("chunk-1")).thenReturn(content);

        List<RagDocumentResponseDTO> documents = service.listDocuments("ops");
        RagDocumentResponseDTO full = service.documentContent("chunk-1");

        assertEquals(1, documents.size());
        assertEquals("runbook.md", documents.get(0).getDisplayName());
        assertEquals("ops", documents.get(0).getTag());
        assertNull(documents.get(0).getContent());
        assertEquals("full content", full.getContent());
        assertEquals("markdown", full.getDocumentType());
        assertEquals(2, full.getChunkIndex());
    }

    @Test
    void absentContentRemainsNullForLegacyControllerContract() {
        KnowledgeRagDocumentApplicationService documentService = mock(KnowledgeRagDocumentApplicationService.class);
        LegacyRagDocumentApplicationService service = new LegacyRagDocumentApplicationService(documentService);
        when(documentService.content("missing")).thenReturn(Map.of());

        assertNull(service.documentContent("missing"));
    }
}
