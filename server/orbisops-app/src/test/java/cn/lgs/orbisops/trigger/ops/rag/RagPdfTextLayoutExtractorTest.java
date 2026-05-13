package cn.lgs.orbisops.trigger.ops.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfTextLayoutExtractorTest {

    @Test
    void positionedTextMustAssembleStablePageLines() throws Exception {
        byte[] bytes = pdfWithLines("first fragment", "second line");
        List<RagPdfTextLayoutExtractor.TextLine> lines;
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            lines = new RagPdfTextLayoutExtractor().extract(pdf);
        }

        assertEquals(2, lines.size());
        assertEquals(1, lines.get(0).page());
        assertEquals(1, lines.get(0).lineNumber());
        assertEquals("first fragment", lines.get(0).text());
        assertEquals(2, lines.get(1).lineNumber());
        assertEquals("second line", lines.get(1).text());
        assertTrue(lines.get(0).x() > 0D);
        assertTrue(lines.get(0).y() > 0D);
        assertTrue(lines.get(0).height() > 0D);
        assertTrue(lines.get(1).y() > lines.get(0).y());
    }

    @Test
    void blankPdfMustProduceNoLayoutLines() throws Exception {
        byte[] bytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            bytes = output.toByteArray();
        }

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(new RagPdfTextLayoutExtractor().extract(pdf).isEmpty());
        }
    }

    private byte[] pdfWithLines(String first, String second) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 720);
                content.showText(first);
                content.newLineAtOffset(0, -20);
                content.showText(second);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
