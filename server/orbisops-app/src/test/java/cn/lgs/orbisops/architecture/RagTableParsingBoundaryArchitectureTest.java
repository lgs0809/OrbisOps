package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTableParsingBoundaryArchitectureTest {

    private static final String PARSER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParser.java";
    private static final String COORDINATOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagDocumentParseCoordinator.java";
    private static final String TABLE_EXTRACTOR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/rag/RagTableChunkExtractor.java";
    private static final String MATERIALIZER = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/knowledge/rag/service/RagChunkMaterializer.java";

    @Test
    void tableProtocolsMustRemainInsideDedicatedExtractor() throws IOException {
        String extractor = read(TABLE_EXTRACTOR);

        assertAll(
                () -> assertTrue(extractor.contains("class RagTableChunkExtractor")),
                () -> assertTrue(extractor.contains("RagChunkDraft")),
                () -> assertTrue(extractor.contains("parseDelimitedLine")),
                () -> assertTrue(extractor.contains("markdownTableHeader")),
                () -> assertTrue(extractor.contains("markdownTableRow")),
                () -> assertTrue(extractor.contains("WorkbookFactory.create")),
                () -> assertTrue(extractor.contains("FormulaEvaluator")),
                () -> assertTrue(extractor.contains("DataFormatter")),
                () -> assertTrue(extractor.contains("normalizeExcelHeader")),
                () -> assertTrue(extractor.contains("excel-sheet-rows")),
                () -> assertFalse(extractor.contains("org.springframework.ai")),
                () -> assertFalse(extractor.contains("TikaDocumentReader")),
                () -> assertFalse(extractor.contains("org.apache.pdfbox")),
                () -> assertFalse(extractor.contains("RagVisualDocumentAnalyzer")));
    }

    @Test
    void coordinatorMustOnlyCoordinateTableExtractionMaterializationAndFallback() throws IOException {
        String parser = read(PARSER);
        String coordinator = read(COORDINATOR);

        assertAll(
                () -> assertTrue(parser.contains("RagDocumentParseCoordinator parseCoordinator")),
                () -> assertFalse(parser.contains("RagTableChunkExtractor")),
                () -> assertTrue(coordinator.contains("RagTableChunkExtractor")),
                () -> assertTrue(coordinator.contains("tableChunkExtractor.extractDelimited(")),
                () -> assertTrue(coordinator.contains("tableChunkExtractor.extractWorkbook(")),
                () -> assertTrue(coordinator.contains("Workbook contains no readable rows.")),
                () -> assertTrue(coordinator.contains("empty-table-text")),
                () -> assertFalse(coordinator.contains("org.apache.poi")),
                () -> assertFalse(coordinator.contains("WorkbookFactory")),
                () -> assertFalse(coordinator.contains("FormulaEvaluator")),
                () -> assertFalse(coordinator.contains("DataFormatter")),
                () -> assertFalse(coordinator.contains("parseDelimitedLine")),
                () -> assertFalse(coordinator.contains("markdownTableHeader")),
                () -> assertFalse(coordinator.contains("markdownTableRow")),
                () -> assertFalse(coordinator.contains("normalizeExcelHeader")),
                () -> assertFalse(coordinator.contains("private record ExcelRow")),
                () -> assertTrue(parser.lines().count() <= 120),
                () -> assertTrue(coordinator.lines().count() <= 230));
    }

    @Test
    void domainChunkMaterializationMustNotAbsorbPoiTypes() throws IOException {
        String materializer = read(MATERIALIZER);

        assertAll(
                () -> assertFalse(materializer.contains("org.apache.poi")),
                () -> assertFalse(materializer.contains("Workbook")),
                () -> assertFalse(materializer.contains("FormulaEvaluator")),
                () -> assertFalse(materializer.contains("DataFormatter")));
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
