package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagBinaryAssetBoundaryArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/rag/RagBinaryAssetPort.java";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/rag/FileRagBinaryAssetAdapter.java";
    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String MULTIMODAL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagMultimodalEmbeddingService.java";
    private static final String PDF_IMAGE_EXTRACTOR =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/rag/RagPdfEmbeddedImageExtractor.java";

    @Test
    void applicationDefinesNarrowFrameworkNeutralBinaryAssetBoundary() throws IOException {
        String port = read(APPLICATION);

        assertAll(
                () -> assertTrue(port.contains("interface RagBinaryAssetPort")),
                () -> assertTrue(port.contains("Path store(")),
                () -> assertTrue(port.contains("boolean isRegularFile(")),
                () -> assertTrue(port.contains("byte[] read(")),
                () -> assertFalse(port.contains("org.springframework")),
                () -> assertFalse(port.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(port.contains("Files.")));
    }

    @Test
    void infrastructureOwnsRootConstrainedFilesystemMechanics() throws IOException {
        String adapter = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(adapter.contains("implements RagBinaryAssetPort")),
                () -> assertTrue(adapter.contains("Files.createDirectories")),
                () -> assertTrue(adapter.contains("Files.write")),
                () -> assertTrue(adapter.contains("Files.readAllBytes")),
                () -> assertTrue(adapter.contains("path.startsWith(root)")),
                () -> assertTrue(adapter.contains("RAG_BINARY_ASSET_PATH_OUTSIDE_ROOT")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void triggerParsesAndEmbedsThroughPortWithoutDirectFilesystemAccess() throws IOException {
        String parser = read(PARSER);
        String multimodal = read(MULTIMODAL);
        String pdfImageExtractor = read(PDF_IMAGE_EXTRACTOR);

        assertAll(
                () -> assertTrue(parser.contains("RagBinaryAssetPort")),
                () -> assertTrue(pdfImageExtractor.contains("binaryAssets.store(")),
                () -> assertTrue(multimodal.contains("RagBinaryAssetPort")),
                () -> assertTrue(multimodal.contains("binaryAssets.isRegularFile(")),
                () -> assertTrue(multimodal.contains("binaryAssets.read(")),
                () -> assertFalse(parser.contains("Files.")),
                () -> assertFalse(multimodal.contains("Files.")));
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
