package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagDocumentKindDetectorTest {

    private final RagDocumentKindDetector detector = new RagDocumentKindDetector();

    @Test
    void routesStructuredFormatsByExtensionAndContentType() {
        assertEquals(RagDocumentKind.MARKDOWN, detector.detect("runbook.md", "text/plain"));
        assertEquals(RagDocumentKind.HTML, detector.detect("page.bin", "text/html"));
        assertEquals(RagDocumentKind.PDF, detector.detect("report.bin", "application/pdf"));
        assertEquals(RagDocumentKind.TABLE_TEXT, detector.detect("metrics.csv", "text/plain"));
        assertEquals(RagDocumentKind.TABLE_BINARY, detector.detect("metrics.xlsx", null));
        assertEquals(RagDocumentKind.IMAGE, detector.detect("diagram.bin", "image/png"));
        assertEquals(RagDocumentKind.CODE, detector.detect("service.java", "text/plain"));
    }

    @Test
    void prioritizesConversationNamesAndFallsBackToTextOrTika() {
        assertEquals(RagDocumentKind.CONVERSATION,
                detector.detect("incident-ticket.txt", "text/plain"));
        assertEquals(RagDocumentKind.TEXT, detector.detect("plain.log", null));
        assertEquals(RagDocumentKind.TIKA,
                detector.detect("archive.docx", "application/octet-stream"));
    }
}
