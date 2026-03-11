package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfChunkProjectorTest {

    private final RagPdfChunkProjector projector =
            new RagPdfChunkProjector(new RagChunkMaterializer());

    @Test
    void paragraphProjectionMustPreserveBoundaryAndPageLineMetadata() {
        List<RagChunkDraft> drafts = projector.paragraphs(
                List.of(
                        new RagPdfParagraphSegmenter.Paragraph(
                                "single page paragraph",
                                1,
                                1,
                                2,
                                4),
                        new RagPdfParagraphSegmenter.Paragraph(
                                "cross page paragraph",
                                1,
                                2,
                                5,
                                8)),
                Map.of("knowledge", "ops", "source", "runbook.pdf"));

        assertEquals(2, drafts.size());
        RagChunkDraft first = drafts.get(0);
        assertEquals(RagChunkDraft.Boundary.STRUCTURE_BOUNDED, first.boundary());
        assertEquals("pdfbox-paragraph", first.metadata().get("chunk_strategy"));
        assertEquals("text", first.metadata().get("chunk_type"));
        assertEquals(0, first.metadata().get("paragraph_index"));
        assertEquals(1, first.metadata().get("page_start"));
        assertEquals(1, first.metadata().get("page_end"));
        assertEquals(1, first.metadata().get("page_number"));
        assertEquals(2, first.metadata().get("line_start"));
        assertEquals(4, first.metadata().get("line_end"));

        RagChunkDraft second = drafts.get(1);
        assertEquals(1, second.metadata().get("paragraph_index"));
        assertEquals(1, second.metadata().get("page_start"));
        assertEquals(2, second.metadata().get("page_end"));
        assertFalse(second.metadata().containsKey("page_number"));
    }

    @Test
    void imageProjectionMustCreateExactEvidenceDraftAndStableMetadata() {
        Path imagePath = Path.of("/tmp/assets/page-001-image-01.png");
        List<RagChunkDraft> drafts = projector.images(
                List.of(new RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence(
                        1,
                        imagePath,
                        "page-001-image-01.png",
                        "abcdef1234567890",
                        "Fig. 1: Retry topology",
                        50D,
                        132D,
                        120D,
                        60D)),
                Map.of("knowledge", "ops", "source", "runbook.pdf"));

        assertEquals(1, drafts.size());
        RagChunkDraft draft = drafts.get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, draft.boundary());
        assertTrue(draft.text().contains("# PDF figure evidence"));
        assertTrue(draft.text().contains("Fig. 1: Retry topology"));
        assertTrue(draft.text().contains(imagePath.toString()));
        assertEquals("pdfbox-image-caption", draft.metadata().get("chunk_strategy"));
        assertEquals("image_figure", draft.metadata().get("chunk_type"));
        assertEquals("image/png", draft.metadata().get("image_mime_type"));
        assertEquals("abcdef1234567890", draft.metadata().get("image_sha256"));
        assertEquals("pdf_embedded_image", draft.metadata().get("visual_source"));
        assertEquals("text", draft.metadata().get("embedding_input_modality"));
        assertEquals("native_image_plus_caption_when_enabled",
                draft.metadata().get("multimodal_embedding_strategy"));
        assertEquals(50D, ((Number) draft.metadata().get("bbox_x")).doubleValue(), 0.01D);
        assertEquals(132D, ((Number) draft.metadata().get("bbox_y")).doubleValue(), 0.01D);
        assertEquals(120D, ((Number) draft.metadata().get("bbox_width")).doubleValue(), 0.01D);
        assertEquals(60D, ((Number) draft.metadata().get("bbox_height")).doubleValue(), 0.01D);
    }

    @Test
    void emptyEvidenceMustProduceNoDrafts() {
        assertTrue(projector.paragraphs(List.of(), Map.of()).isEmpty());
        assertTrue(projector.images(List.of(), Map.of()).isEmpty());
    }
}
