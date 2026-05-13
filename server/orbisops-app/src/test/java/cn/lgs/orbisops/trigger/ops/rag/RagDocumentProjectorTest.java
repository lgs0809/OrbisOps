package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagDocumentProjectorTest {

    private final RagDocumentProjector projector =
            new RagDocumentProjector(new RagChunkMaterializer());

    @Test
    void materializesDomainChunksAndProjectsStableSpringDocuments() {
        List<Document> documents = projector.materialize(List.of(
                RagChunkDraft.exact("evidence", metadata())));

        assertEquals(1, documents.size());
        assertEquals("evidence", documents.get(0).getText());
        assertEquals("PROJECT", documents.get(0).getMetadata().get("knowledge_scope"));
        assertEquals(0, documents.get(0).getMetadata().get("chunk_index"));
    }

    @Test
    void plainTextUsesStructureBoundedMaterialization() {
        List<Document> documents = projector.plainText("A\n\nB", metadata());

        assertEquals(1, documents.size());
        assertEquals("A\n\nB", documents.get(0).getText());
    }

    private Map<String, Object> metadata() {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "lock-recovery",
                "rag_name", "Operations",
                "source", "runbook.txt",
                "max_segment_chars", 1_000,
                "hard_split_overlap_chars", 100);
    }
}
