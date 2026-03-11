package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTikaVisualFallbackBoundaryArchitectureTest {

    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParseCoordinator.java";
    private static final String PROJECTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentProjector.java";
    private static final String TIKA = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagTikaChunkExtractor.java";
    private static final String VISUAL = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagVisualFallbackCoordinator.java";
    private static final String POLICY = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagVisualFallbackPolicy.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";

    @Test
    void tikaProtocolMustRemainInsideDedicatedExtractor() throws IOException {
        String tika = read(TIKA);

        assertAll(
                () -> assertTrue(tika.contains("class RagTikaChunkExtractor")),
                () -> assertTrue(tika.contains("TikaDocumentReader")),
                () -> assertTrue(tika.contains("AbstractResource")),
                () -> assertTrue(tika.contains("RAG file resource")),
                () -> assertTrue(tika.contains("structured_rows")),
                () -> assertTrue(tika.contains("tika_text_fallback")),
                () -> assertTrue(tika.contains("Document parsing failed:")),
                () -> assertTrue(tika.contains("Function<Resource, List<Document>>")),
                () -> assertFalse(tika.contains("RagVisualDocumentAnalyzer")),
                () -> assertFalse(tika.contains("org.apache.pdfbox")),
                () -> assertFalse(tika.contains("org.apache.poi")));
    }

    @Test
    void visualCoordinationAndPolicyMustRemainOutsideParser() throws IOException {
        String visual = read(VISUAL);
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(visual.contains("class RagVisualFallbackCoordinator")),
                () -> assertTrue(visual.contains("visual_parse_reason")),
                () -> assertTrue(visual.contains("pdfbox_no_extractable_text_or_image")),
                () -> assertTrue(visual.contains("image-placeholder")),
                () -> assertTrue(visual.contains("analyzer.analyzePdf")),
                () -> assertTrue(visual.contains("analyzer.analyzeImage")),
                () -> assertTrue(visual.contains("record Result")),
                () -> assertTrue(policy.contains("class RagVisualFallbackPolicy")),
                () -> assertTrue(policy.contains("recommended_manual_enable")),
                () -> assertTrue(policy.contains("skipped_low_value_or_cost_gate")),
                () -> assertFalse(policy.contains("org.springframework")));
    }

    @Test
    void coordinatorMustOnlyRouteTikaAndResolveVisualResults() throws IOException {
        String parser = read(PARSER);
        String coordinator = read(COORDINATOR);
        String projector = read(PROJECTOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagTikaChunkExtractor")),
                () -> assertFalse(parser.contains("RagVisualFallbackCoordinator")),
                () -> assertTrue(coordinator.contains("RagTikaChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagVisualFallbackCoordinator")),
                () -> assertTrue(coordinator.contains("tikaChunkExtractor.extract")),
                () -> assertTrue(coordinator.contains("visualFallbackCoordinator.pdfFallback")),
                () -> assertTrue(coordinator.contains("visualFallbackCoordinator.image")),
                () -> assertTrue(coordinator.contains("projector.visual(")),
                () -> assertTrue(projector.contains("List<Document> visual(")),
                () -> assertFalse(coordinator.contains("TikaDocumentReader")),
                () -> assertFalse(coordinator.contains("AbstractResource")),
                () -> assertFalse(coordinator.contains("private List<Document> parseTika")),
                () -> assertFalse(coordinator.contains("private Resource tikaResource")),
                () -> assertFalse(coordinator.contains("private List<Document> parseImage")),
                () -> assertFalse(coordinator.contains("private List<Document> imagePlaceholder")),
                () -> assertFalse(coordinator.contains("private String visualPolicyStatus")),
                () -> assertFalse(coordinator.contains("private boolean highValueCandidate")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(coordinator.lines().count() <= 230));
    }

    @Test
    void domainMaterializerMustRemainFreeOfTikaVisualProtocols() throws IOException {
        String materializer = read(MATERIALIZER);

        assertAll(
                () -> assertFalse(materializer.contains("TikaDocumentReader")),
                () -> assertFalse(materializer.contains("RagVisualDocumentAnalyzer")),
                () -> assertFalse(materializer.contains("visual_parse_reason")),
                () -> assertFalse(materializer.contains("image-placeholder")));
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
