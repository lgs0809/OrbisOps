package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualMediaProtocolBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";

    @Test
    void mediaAndHttpProtocolMustHaveSeparateOwners() throws IOException {
        String analyzer = read(RAG + "RagVisualDocumentAnalyzer.java");
        String media = read(RAG + "RagVisualMediaPreparer.java");
        String protocol = read(RAG + "RagVisualAnalysisProtocol.java");

        assertAll(
                () -> assertTrue(analyzer.contains("mediaPreparer.prepareImage(file)")),
                () -> assertTrue(analyzer.contains("mediaPreparer.preparePdf(file)")),
                () -> assertTrue(analyzer.contains("protocol.analyze(")),
                () -> assertFalse(analyzer.contains("Loader.loadPDF")),
                () -> assertFalse(analyzer.contains("PDFRenderer")),
                () -> assertFalse(analyzer.contains("ImageIO")),
                () -> assertFalse(analyzer.contains("HttpClient")),
                () -> assertFalse(analyzer.contains("HttpRequest")),
                () -> assertFalse(analyzer.contains("Base64")),
                () -> assertFalse(analyzer.contains("response_format")),
                () -> assertFalse(analyzer.contains("Authorization")),
                () -> assertTrue(media.contains("Loader.loadPDF(file.getBytes())")),
                () -> assertTrue(media.contains("new PDFRenderer(pdf)")),
                () -> assertTrue(media.contains("renderImageWithDPI(")),
                () -> assertTrue(media.contains("ImageIO.write(image, \"png\"")),
                () -> assertTrue(media.contains("settings.maxImagesPerDocument()")),
                () -> assertTrue(media.contains("settings.maxImageBytes()")),
                () -> assertTrue(media.contains("settings.pdfRenderDpi()")),
                () -> assertTrue(media.contains("record PreparedImage(")),
                () -> assertTrue(media.contains("record PdfPreparation(")),
                () -> assertFalse(media.contains("HttpClient")),
                () -> assertFalse(media.contains("HttpRequest")),
                () -> assertFalse(media.contains("JSONObject")),
                () -> assertFalse(media.contains("org.springframework.ai.document.Document")),
                () -> assertTrue(protocol.contains("HttpClient.newBuilder()")),
                () -> assertTrue(protocol.contains("HttpRequest.newBuilder()")),
                () -> assertTrue(protocol.contains("Base64.getEncoder()")),
                () -> assertTrue(protocol.contains("\"response_format\"")),
                () -> assertTrue(protocol.contains("\"Authorization\"")),
                () -> assertTrue(protocol.contains("\"max_completion_tokens\"")),
                () -> assertTrue(protocol.contains("retryable(int statusCode)")),
                () -> assertTrue(protocol.contains("interface HttpTransport")),
                () -> assertTrue(protocol.contains("interface RetryDelay")),
                () -> assertFalse(protocol.contains("PDFRenderer")),
                () -> assertFalse(protocol.contains("ImageIO")),
                () -> assertFalse(protocol.contains("RagFileResource")),
                () -> assertFalse(protocol.contains("org.springframework.ai.document.Document")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
