package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDocumentParserFacadeBoundaryArchitectureTest {

    private static final String RAG = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/";
    private static final String APPLICATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/rag/";

    @Test
    void parserMustOnlyNormalizeRequestCreateMetadataAndDelegateRoute() throws IOException {
        String parser = read(RAG + "RagDocumentParser.java");
        String settings = read(RAG + "RagDocumentParserSettings.java");
        String detector = read(RAG + "RagDocumentKindDetector.java");
        String metadata = read(RAG + "RagDocumentMetadataFactory.java");
        String coordinator = read(RAG + "RagDocumentParseCoordinator.java");
        String reader = read(RAG + "RagTextResourceReader.java");
        String projector = read(RAG + "RagDocumentProjector.java");
        String configuration = read(APPLICATION + "RagDocumentParserConfiguration.java");

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParserSettings settings")),
                () -> assertTrue(parser.contains("RagDocumentKindDetector kindDetector")),
                () -> assertTrue(parser.contains("RagDocumentMetadataFactory metadataFactory")),
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertTrue(parser.contains("legacyConstructorDefaults()")),
                () -> assertTrue(parser.contains("kindDetector.detect(")),
                () -> assertTrue(parser.contains("metadataFactory.create(")),
                () -> assertTrue(parser.contains("parseCoordinator.parse(")),
                () -> assertTrue(parser.contains("ObjectProvider<RagVisualDocumentAnalyzer>")),
                () -> assertFalse(parser.contains("@Value")),
                () -> assertFalse(parser.contains("@Autowired(required = false)")),
                () -> assertFalse(parser.contains("CODE_EXTENSIONS")),
                () -> assertFalse(parser.contains("IMAGE_EXTENSIONS")),
                () -> assertFalse(parser.contains("switch (kind)")),
                () -> assertFalse(parser.contains("TikaDocumentReader")),
                () -> assertFalse(parser.contains("new Document(")),
                () -> assertFalse(parser.contains("BufferedReader")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(settings.contains("public record RagDocumentParserSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(detector.contains("CODE_EXTENSIONS")),
                () -> assertTrue(detector.contains("IMAGE_EXTENSIONS")),
                () -> assertTrue(detector.contains("RagDocumentKind detect(")),
                () -> assertFalse(detector.contains("@Service")),
                () -> assertTrue(metadata.contains("ops-rag-structured-v1")),
                () -> assertTrue(metadata.contains("high_value_candidate")),
                () -> assertFalse(metadata.contains("@Service")),
                () -> assertTrue(coordinator.contains("switch (kind)")),
                () -> assertTrue(coordinator.contains("RagTextResourceReader textReader")),
                () -> assertTrue(coordinator.contains("RagDocumentProjector projector")),
                () -> assertTrue(coordinator.contains("tika-fallback")),
                () -> assertFalse(coordinator.contains("@Service")),
                () -> assertTrue(coordinator.lines().count() <= 230),
                () -> assertTrue(reader.contains("StandardCharsets.UTF_8")),
                () -> assertTrue(reader.contains("value.startsWith(\"\\uFEFF\")")),
                () -> assertTrue(projector.contains("chunkMaterializer.materialize(drafts)")),
                () -> assertTrue(projector.contains("new Document(document.id(), document.text(), document.metadata())")),
                () -> assertTrue(configuration.contains("orbisops.rag.parse.max-chunk-chars")),
                () -> assertTrue(configuration.contains("orbisops.rag.parse.max-pdf-images")));
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
