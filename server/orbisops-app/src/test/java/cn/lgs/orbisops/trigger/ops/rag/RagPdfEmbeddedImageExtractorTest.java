package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagPdfEmbeddedImageExtractorTest {

    @Test
    void imageEvidenceMustStorePngWithStablePathHashCaptionAndBounds() throws Exception {
        RagBinaryAssetPort assets = mock(RagBinaryAssetPort.class);
        Path stored = Path.of("/tmp/pdf-assets/image.png");
        when(assets.store(anyString(), anyString(), anyString(), any(byte[].class)))
                .thenReturn(stored);
        RagPdfEmbeddedImageExtractor extractor = new RagPdfEmbeddedImageExtractor(
                assets,
                new RagPdfFigureCaptionPolicy());
        List<RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence> evidence;
        try (PDDocument pdf = Loader.loadPDF(pdfWithImage())) {
            evidence = extractor.extract(
                    pdf,
                    Map.of(
                            "knowledge", "demo ops",
                            "source", "incident report.pdf"),
                    List.of(line("Fig. 1: Retry topology", 200D)),
                    10);
        }

        assertEquals(1, evidence.size());
        RagPdfEmbeddedImageExtractor.EmbeddedImageEvidence image = evidence.get(0);
        assertEquals(1, image.pageNumber());
        assertEquals(stored, image.imagePath());
        assertTrue(image.imageFileName().matches(
                "page-001-image-01-[0-9a-f]{12}\\.png"));
        assertTrue(image.sha256().length() >= 12);
        assertEquals("Fig. 1: Retry topology", image.caption());
        assertEquals(50D, image.bboxX(), 0.1D);
        assertEquals(132D, image.bboxY(), 1D);
        assertEquals(120D, image.bboxWidth(), 0.1D);
        assertEquals(60D, image.bboxHeight(), 0.1D);

        ArgumentCaptor<String> knowledge = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> source = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fileName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(assets).store(
                knowledge.capture(),
                source.capture(),
                fileName.capture(),
                bytes.capture());
        assertEquals("demo_ops", knowledge.getValue());
        assertEquals("incident_report", source.getValue());
        assertEquals(image.imageFileName(), fileName.getValue());
        assertTrue(bytes.getValue().length > 0);
    }

    @Test
    void zeroImageLimitMustAvoidPageProcessingAndStorage() throws Exception {
        RagBinaryAssetPort assets = mock(RagBinaryAssetPort.class);
        RagPdfEmbeddedImageExtractor extractor = new RagPdfEmbeddedImageExtractor(
                assets,
                new RagPdfFigureCaptionPolicy());

        try (PDDocument pdf = Loader.loadPDF(pdfWithImage())) {
            assertTrue(extractor.extract(
                    pdf,
                    Map.of(),
                    List.of(),
                    0).isEmpty());
        }

        verify(assets, never()).store(
                anyString(),
                anyString(),
                anyString(),
                any(byte[].class));
    }

    private byte[] pdfWithImage() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(
                    document,
                    testImage());
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 50, 600, 120, 60);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private BufferedImage testImage() {
        BufferedImage image = new BufferedImage(
                40,
                20,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 40, 20);
        graphics.setColor(Color.RED);
        graphics.fillRect(4, 4, 32, 12);
        graphics.dispose();
        return image;
    }

    private RagPdfTextLayoutExtractor.TextLine line(
            String text,
            double y) {
        return new RagPdfTextLayoutExtractor.TextLine(
                1,
                1,
                text,
                50D,
                y,
                10D);
    }
}
