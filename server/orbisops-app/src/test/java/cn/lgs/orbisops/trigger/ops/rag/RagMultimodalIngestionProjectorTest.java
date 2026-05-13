package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagMultimodalIngestionProjectorTest {

    @Test
    void textProjectionMustBuildStableIdTruncatedContentAndMultimodalMetadata() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(4));
        Document document = new Document(
                "doc-1",
                "abcdef",
                Map.of("source", "manual.md", "knowledge", "ops"));

        RagMultimodalIngestionProjector.TextProjection projection = projector
                .projectText(document)
                .orElseThrow();

        assertEquals("doc-1:mm:text", projection.id());
        assertEquals("abcd...", projection.content());
        assertEquals("manual.md", projection.source());
        assertEquals("ops", projection.metadata().get("knowledge"));
        assertEquals("text", projection.metadata().get("multimodal_media_type"));
        assertEquals("text", projection.metadata().get("embedding_input_modality"));
        assertEquals("text_in_shared_multimodal_space", projection.metadata().get("multimodal_embedding_strategy"));
        assertEquals("qwen-vl", projection.metadata().get("multimodal_embedding_provider"));
        assertEquals("model", projection.metadata().get("multimodal_embedding_model"));
        assertEquals(256, projection.metadata().get("multimodal_embedding_dimension"));
        assertEquals("table_name", projection.metadata().get("multimodal_table"));
        assertEquals("multimodal", projection.metadata().get("retrieval_source"));
    }

    @Test
    void nullAndBlankTextMustNotProduceProjection() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(3000));

        assertTrue(projector.projectText(null).isEmpty());
        assertTrue(projector.projectText(new Document("blank", "  ", Map.of())).isEmpty());
    }

    @Test
    void mediaProjectionMustSelectMatchingPageAndBuildContentMetadataHashAndId() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(8));
        Document first = new Document(
                "doc-1",
                "first page",
                Map.of("source", "manual.pdf", "page_number", 1));
        Document second = new Document(
                "doc-2",
                "second page text",
                Map.of("source", "manual.pdf", "visual_page_number", "2"));
        byte[] bytes = new byte[]{1, 2, 3};
        RagMultimodalMediaPreparer.PreparedMedia media = new RagMultimodalMediaPreparer.PreparedMedia(
                bytes, "image/png", 2, "pdf_page");

        RagMultimodalIngestionProjector.MediaProjection projection = projector.projectMedia(
                List.of(first, second),
                media);

        String hash = String.valueOf(projection.metadata().get("multimodal_image_sha256"));
        assertTrue(projection.id().startsWith("doc-2:mm:pdf_page:2:"));
        assertTrue(projection.id().endsWith(hash));
        assertEquals(64, hash.length());
        assertTrue(projection.content().contains("Source: manual.pdf"));
        assertTrue(projection.content().contains("Media type: pdf_page"));
        assertTrue(projection.content().contains("Page: 2"));
        assertTrue(projection.content().contains("second p..."));
        assertEquals("manual.pdf", projection.source());
        assertEquals("pdf_page", projection.metadata().get("multimodal_media_type"));
        assertEquals("pdf_page", projection.metadata().get("visual_source"));
        assertEquals(2, projection.metadata().get("visual_page_number"));
        assertEquals("image", projection.metadata().get("embedding_input_modality"));
        assertEquals("native_image_embedding", projection.metadata().get("multimodal_embedding_strategy"));
        assertEquals("image/png", projection.metadata().get("multimodal_image_mime_type"));
        assertArrayEquals(bytes, projection.bytes());

        bytes[0] = 9;
        byte[] returned = projection.bytes();
        returned[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, projection.bytes());
    }

    @Test
    void emptyDocumentListMustUseStableFallbackProjection() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(3000));
        RagMultimodalMediaPreparer.PreparedMedia media = new RagMultimodalMediaPreparer.PreparedMedia(
                new byte[]{4}, "image/png", 1, "image");

        RagMultimodalIngestionProjector.MediaProjection projection = projector.projectMedia(List.of(), media);

        assertTrue(projection.id().startsWith("multimodal-empty:mm:image:1:"));
        assertTrue(projection.content().contains("Source: unknown"));
        assertTrue(projection.content().contains("Multimodal media document."));
        assertEquals("unknown", projection.source());
    }

    @Test
    void pageNumberMustSupportNumberStringAndInvalidValues() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(3000));

        assertEquals(3, projector.pageNumber(3));
        assertEquals(4, projector.pageNumber("4"));
        assertEquals(-1, projector.pageNumber("four"));
        assertEquals(-1, projector.pageNumber(null));
    }

    @Test
    void projectionMetadataMustBeImmutable() {
        RagMultimodalIngestionProjector projector = new RagMultimodalIngestionProjector(settings(3000));
        RagMultimodalIngestionProjector.TextProjection projection = projector
                .projectText(new Document("doc", "text", Map.of()))
                .orElseThrow();

        boolean mutationFailed = false;
        try {
            projection.metadata().put("changed", true);
        } catch (UnsupportedOperationException expected) {
            mutationFailed = true;
        }
        assertTrue(mutationFailed);
        assertFalse(projection.metadata().containsKey("changed"));
    }

    private RagMultimodalSettings settings(int maxTextChars) {
        return new RagMultimodalSettings(
                true,
                "qwen-vl",
                "http://localhost",
                "test-credential",
                "v1/embed",
                "model",
                "table_name",
                256,
                true,
                true,
                true,
                true,
                3,
                144,
                20_971_520L,
                maxTextChars,
                8,
                30,
                1);
    }
}
