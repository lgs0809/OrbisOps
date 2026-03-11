package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagDocumentMetadataFactoryTest {

    @Test
    void projectsStableParserScopeAndVisualCandidateMetadata() {
        RagFileResource file = mock(RagFileResource.class);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(1_024L);
        RagDocumentMetadataFactory factory = new RagDocumentMetadataFactory(
                new RagVisualFallbackPolicy());

        Map<String, Object> metadata = factory.create(
                "Operations",
                "lock-recovery",
                file,
                "incident.pdf",
                RagDocumentKind.PDF,
                new RagParsePolicy("PROJECT", "demo-project", 1_000, 100));

        assertEquals("Operations", metadata.get("rag_name"));
        assertEquals("lock-recovery", metadata.get("knowledge"));
        assertEquals("incident.pdf", metadata.get("source"));
        assertEquals("application/pdf", metadata.get("content_type"));
        assertEquals(1_024L, metadata.get("file_size"));
        assertEquals("pdf", metadata.get("document_type"));
        assertEquals("ops-rag-structured-v1", metadata.get("parser_version"));
        assertEquals("PROJECT", metadata.get("knowledge_scope"));
        assertEquals("demo-project", metadata.get("project_id"));
        assertEquals(1_000, metadata.get("max_segment_chars"));
        assertEquals(100, metadata.get("hard_split_overlap_chars"));
        assertTrue(metadata.containsKey("high_value_candidate"));
    }
}
