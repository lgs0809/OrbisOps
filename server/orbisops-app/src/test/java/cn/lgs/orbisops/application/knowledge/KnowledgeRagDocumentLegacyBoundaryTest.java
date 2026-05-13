package cn.lgs.orbisops.application.knowledge;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRagDocumentLegacyBoundaryTest {

    @Test
    void legacyDeleteAndContentRemainInsideTypedApplicationService() {
        KnowledgeRagDocumentPort port = mock(KnowledgeRagDocumentPort.class);
        KnowledgeDocumentCatalogApplicationService catalogService = mock(KnowledgeDocumentCatalogApplicationService.class);
        KnowledgeProjectExistencePort projectPort = mock(KnowledgeProjectExistencePort.class);
        KnowledgeRagDocumentApplicationService service = new KnowledgeRagDocumentApplicationService(
                port,
                catalogService,
                projectPort);
        KnowledgeRagChunk chunk = new KnowledgeRagChunk(
                "chunk-1",
                "ops",
                "chunk-1",
                "runbook.md",
                "runbook.md",
                "markdown",
                2,
                "section",
                12L,
                "full content",
                true,
                "");
        when(port.deleteChunk("chunk-1")).thenReturn(true);
        when(port.content("chunk-1")).thenReturn(chunk);

        assertTrue(service.deleteChunk(" chunk-1 "));
        Map<String, Object> content = service.content(" chunk-1 ");

        verify(port).deleteChunk("chunk-1");
        verify(port).content("chunk-1");
        assertEquals("chunk-1", content.get("chunkId"));
        assertEquals("ops", content.get("knowledgeTag"));
        assertEquals("full content", content.get("content"));
        assertEquals(12L, content.get("size"));
    }
}
