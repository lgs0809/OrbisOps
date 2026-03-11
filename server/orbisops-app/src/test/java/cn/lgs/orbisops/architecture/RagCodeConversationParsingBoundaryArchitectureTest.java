package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCodeConversationParsingBoundaryArchitectureTest {

    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParseCoordinator.java";
    private static final String CODE_EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagCodeChunkExtractor.java";
    private static final String CONVERSATION_EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagConversationChunkExtractor.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";

    @Test
    void codeProtocolMustRemainInsideDedicatedExtractor() throws IOException {
        String extractor = read(CODE_EXTRACTOR);

        assertAll(
                () -> assertTrue(extractor.contains("class RagCodeChunkExtractor")),
                () -> assertTrue(extractor.contains("CODE_BOUNDARY_PATTERN")),
                () -> assertTrue(extractor.contains("CODE_PACKAGE_PATTERN")),
                () -> assertTrue(extractor.contains("codeSymbol")),
                () -> assertTrue(extractor.contains("code-symbol")),
                () -> assertTrue(extractor.contains("line_start")),
                () -> assertTrue(extractor.contains("line_end")),
                () -> assertFalse(extractor.contains("org.springframework.ai")),
                () -> assertFalse(extractor.contains("org.apache.poi")),
                () -> assertFalse(extractor.contains("org.apache.pdfbox")),
                () -> assertFalse(extractor.contains("TikaDocumentReader")));
    }

    @Test
    void conversationProtocolMustRemainInsideDedicatedExtractor() throws IOException {
        String extractor = read(CONVERSATION_EXTRACTOR);

        assertAll(
                () -> assertTrue(extractor.contains("class RagConversationChunkExtractor")),
                () -> assertTrue(extractor.contains("CONVERSATION_TURN_PATTERN")),
                () -> assertTrue(extractor.contains("LinkedHashSet")),
                () -> assertTrue(extractor.contains("conversation-turns")),
                () -> assertTrue(extractor.contains("turn_start")),
                () -> assertTrue(extractor.contains("turn_end")),
                () -> assertTrue(extractor.contains("looksLikeConversation")),
                () -> assertTrue(extractor.contains("record Extraction")),
                () -> assertFalse(extractor.contains("org.springframework.ai")),
                () -> assertFalse(extractor.contains("RagTextMarkupChunkExtractor")),
                () -> assertFalse(extractor.contains("TikaDocumentReader")));
    }

    @Test
    void coordinatorMustOnlyCoordinateCodeConversationExtractionAndFallback() throws IOException {
        String parser = read(PARSER);
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagCodeChunkExtractor")),
                () -> assertFalse(parser.contains("RagConversationChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagCodeChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagConversationChunkExtractor")),
                () -> assertTrue(coordinator.contains("codeChunkExtractor.extract(")),
                () -> assertTrue(coordinator.contains("textReader.read(file)")),
                () -> assertTrue(coordinator.contains("conversationChunkExtractor.extract(text, baseMetadata)")),
                () -> assertTrue(coordinator.contains("conversationChunkExtractor.looksLikeConversation(text)")),
                () -> assertTrue(coordinator.contains("conversation-paragraph-fallback")),
                () -> assertFalse(coordinator.contains("CODE_BOUNDARY_PATTERN")),
                () -> assertFalse(coordinator.contains("CODE_PACKAGE_PATTERN")),
                () -> assertFalse(coordinator.contains("CONVERSATION_TURN_PATTERN")),
                () -> assertFalse(coordinator.contains("private List<Document> parseCode")),
                () -> assertFalse(coordinator.contains("private boolean isCodeBoundary")),
                () -> assertFalse(coordinator.contains("private String codeSymbol")),
                () -> assertFalse(coordinator.contains("private boolean looksLikeConversation")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(coordinator.lines().count() <= 230));
    }

    @Test
    void domainMaterializerMustRemainFreeOfCodeConversationProtocols() throws IOException {
        String materializer = read(MATERIALIZER);

        assertAll(
                () -> assertFalse(materializer.contains("CODE_BOUNDARY_PATTERN")),
                () -> assertFalse(materializer.contains("CODE_PACKAGE_PATTERN")),
                () -> assertFalse(materializer.contains("CONVERSATION_TURN_PATTERN")),
                () -> assertFalse(materializer.contains("turn_start")),
                () -> assertFalse(materializer.contains("code-symbol")));
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
