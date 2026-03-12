package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTextMarkupParsingBoundaryArchitectureTest {

    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParseCoordinator.java";
    private static final String EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagTextMarkupChunkExtractor.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";

    @Test
    void markupProtocolsMustRemainInsideDedicatedExtractor() throws IOException {
        String extractor = read(EXTRACTOR);

        assertAll(
                () -> assertTrue(extractor.contains("class RagTextMarkupChunkExtractor")),
                () -> assertTrue(extractor.contains("MARKDOWN_HEADING_PATTERN")),
                () -> assertTrue(extractor.contains("MARKDOWN_IMAGE_PATTERN")),
                () -> assertTrue(extractor.contains("String[] headingStack = new String[6]")),
                () -> assertTrue(extractor.contains("markdown-image-reference")),
                () -> assertTrue(extractor.contains("htmlToMarkdown")),
                () -> assertTrue(extractor.contains("looksLikeMarkdown")),
                () -> assertTrue(extractor.contains("html-to-markdown")),
                () -> assertFalse(extractor.contains("org.springframework.ai")),
                () -> assertFalse(extractor.contains("org.apache.poi")),
                () -> assertFalse(extractor.contains("org.apache.pdfbox")),
                () -> assertFalse(extractor.contains("TikaDocumentReader")),
                () -> assertFalse(extractor.contains("RagVisualDocumentAnalyzer")));
    }

    @Test
    void coordinatorMustOnlyCoordinateMarkupExtractionAndMaterialization() throws IOException {
        String parser = read(PARSER);
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagTextMarkupChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagTextMarkupChunkExtractor")),
                () -> assertTrue(coordinator.contains("textMarkupChunkExtractor.extractMarkdown")),
                () -> assertTrue(coordinator.contains("textMarkupChunkExtractor.extractHtml")),
                () -> assertTrue(coordinator.contains("textMarkupChunkExtractor.looksLikeMarkdown(text)")),
                () -> assertTrue(coordinator.contains("looksLikeConversation(text)")),
                () -> assertFalse(coordinator.contains("MARKDOWN_HEADING_PATTERN")),
                () -> assertFalse(coordinator.contains("MARKDOWN_IMAGE_PATTERN")),
                () -> assertFalse(coordinator.contains("private List<Document> parseMarkdown")),
                () -> assertFalse(coordinator.contains("appendMarkdownImageDrafts")),
                () -> assertFalse(coordinator.contains("htmlToMarkdown")),
                () -> assertFalse(coordinator.contains("cleanupMarkdownTitle")),
                () -> assertFalse(coordinator.contains("stripMarkdownImagePath")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(coordinator.lines().count() <= 230));
    }

    @Test
    void domainMaterializerMustRemainFreeOfMarkupProtocols() throws IOException {
        String materializer = read(MATERIALIZER);

        assertAll(
                () -> assertFalse(materializer.contains("MARKDOWN_HEADING_PATTERN")),
                () -> assertFalse(materializer.contains("MARKDOWN_IMAGE_PATTERN")),
                () -> assertFalse(materializer.contains("htmlToMarkdown")),
                () -> assertFalse(materializer.contains("heading_path")),
                () -> assertFalse(materializer.contains("markdown-image-reference")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
