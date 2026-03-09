package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagVisualDocumentProjectionBoundaryArchitectureTest {

    private static final String RAG =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/";

    @Test
    void documentParsingRenderingAndFallbackProjectionMustStayOutsideAnalyzer() throws IOException {
        String analyzer = read(RAG + "RagVisualDocumentAnalyzer.java");
        String projector = read(RAG + "RagVisualDocumentProjector.java");

        assertAll(
                () -> assertTrue(analyzer.contains("projector.sourceMetadata(")),
                () -> assertTrue(analyzer.contains("projector.tooLarge(")),
                () -> assertTrue(analyzer.contains("projector.refused(")),
                () -> assertTrue(analyzer.contains("projector.project(")),
                () -> assertTrue(analyzer.contains("projector.failure(")),
                () -> assertFalse(analyzer.contains("JSONObject")),
                () -> assertFalse(analyzer.contains("JSONArray")),
                () -> assertFalse(analyzer.contains("new Document(")),
                () -> assertFalse(analyzer.contains("UUID.nameUUIDFromBytes")),
                () -> assertFalse(analyzer.contains("parseVisualContent(")),
                () -> assertFalse(analyzer.contains("formatVisualDocument(")),
                () -> assertFalse(analyzer.contains("visual_parse_reason")),
                () -> assertFalse(analyzer.contains("visual_confidence")),
                () -> assertTrue(analyzer.contains("log.warn(")),
                () -> assertTrue(analyzer.lines().count() <= 140),
                () -> assertTrue(projector.contains("parseVisualContent(")),
                () -> assertTrue(projector.contains("applyVisualDefaults(")),
                () -> assertTrue(projector.contains("formatVisualDocument(")),
                () -> assertTrue(projector.contains("new Document(")),
                () -> assertTrue(projector.contains("UUID.nameUUIDFromBytes(")),
                () -> assertTrue(projector.contains("visual_parse_status")),
                () -> assertTrue(projector.contains("visual_parse_reason")),
                () -> assertTrue(projector.contains("visual_confidence")),
                () -> assertTrue(projector.contains("visual-structured-description")),
                () -> assertTrue(projector.contains("visual_to_text_then_text_embedding")),
                () -> assertTrue(projector.contains("public Document project(")),
                () -> assertFalse(projector.contains("HttpClient")),
                () -> assertFalse(projector.contains("HttpRequest")),
                () -> assertFalse(projector.contains("PDFRenderer")),
                () -> assertFalse(projector.contains("ImageIO")),
                () -> assertFalse(projector.contains("RagFileResource")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertFalse(projector.contains("@Value")));
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
