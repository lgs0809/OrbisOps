package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagTableChunkExtractorTest {

    private final RagTableChunkExtractor extractor = new RagTableChunkExtractor(new RagChunkMaterializer());

    @Test
    void shouldParseQuotedCsvEscapeMarkdownAndTruncateExtraCells() {
        String csv = "\uFEFFtraceId,description,owner\r\n"
                + "\"abc,123\",\"lock \"\"retry\"\" | queue\",ops,ignored\r\n";

        List<RagChunkDraft> drafts = extractor.extractDelimited(csv, metadata(1000), "errors.csv");

        assertEquals(1, drafts.size());
        RagChunkDraft draft = drafts.get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, draft.boundary());
        assertTrue(draft.text().contains("| traceId | description | owner |"));
        assertTrue(draft.text().contains("| abc,123 | lock \"retry\" \\| queue | ops |"));
        assertFalse(draft.text().contains("ignored"));
        assertEquals("traceId,description,owner", draft.metadata().get("columns"));
        assertEquals(1, draft.metadata().get("row_start"));
        assertEquals(1, draft.metadata().get("row_end"));
        assertEquals("table-markdown-rows", draft.metadata().get("chunk_strategy"));
    }

    @Test
    void shouldUseTsvDelimiterAndPadMissingCells() {
        String tsv = "traceId\terrorCode\taction\nabc123\tERR_LOCK_001\n";

        List<RagChunkDraft> drafts = extractor.extractDelimited(tsv, metadata(1000), "errors.tsv");

        assertEquals(1, drafts.size());
        assertTrue(drafts.get(0).text().contains("| abc123 | ERR_LOCK_001 |  |"));
        assertEquals("traceId,errorCode,action", drafts.get(0).metadata().get("columns"));
    }

    @Test
    void shouldRepeatHeaderAndPreserveRowRangesWhenDelimitedRowsExceedLimit() {
        String longValue = "X".repeat(650);
        String csv = "id,payload\n"
                + "1," + longValue + "\n"
                + "2," + longValue + "\n"
                + "3," + longValue + "\n";

        List<RagChunkDraft> drafts = extractor.extractDelimited(csv, metadata(1000), "large.csv");

        assertEquals(3, drafts.size());
        assertEquals(List.of(1, 2, 3), drafts.stream()
                .map(draft -> (Integer) draft.metadata().get("row_start"))
                .toList());
        assertEquals(List.of(1, 2, 3), drafts.stream()
                .map(draft -> (Integer) draft.metadata().get("row_end"))
                .toList());
        assertTrue(drafts.stream().allMatch(draft -> draft.text().startsWith("| id | payload |\n| --- | --- |\n")));
        assertTrue(drafts.get(0).text().contains("| 1 |"));
        assertTrue(drafts.get(1).text().contains("| 2 |"));
        assertTrue(drafts.get(2).text().contains("| 3 |"));
    }

    @Test
    void shouldKeepHeaderOnlyDelimitedTableMetadata() {
        List<RagChunkDraft> drafts = extractor.extractDelimited(
                "code,action\n",
                metadata(1000),
                "header-only.csv");

        assertEquals(1, drafts.size());
        assertEquals(1, drafts.get(0).metadata().get("row_start"));
        assertEquals(0, drafts.get(0).metadata().get("row_end"));
        assertEquals("| code | action |\n| --- | --- |", drafts.get(0).text().trim());
    }

    @Test
    void shouldExtractWorkbookWithNormalizedHeaderFormulaAndOriginalRowNumbers() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet metrics = workbook.createSheet("metrics");
            Row header = metrics.createRow(0);
            header.createCell(0).setCellValue("metric");
            header.createCell(1).setCellValue("");
            Row data = metrics.createRow(2);
            data.createCell(0).setCellValue("latency");
            Cell formula = data.createCell(1);
            formula.setCellFormula("1+2");

            Sheet headerOnly = workbook.createSheet("header_only");
            Row secondHeader = headerOnly.createRow(4);
            secondHeader.createCell(0).setCellValue("status");

            workbook.write(output);
            bytes = output.toByteArray();
        }

        List<RagChunkDraft> drafts = extractor.extractWorkbook(file("metrics.xlsx", bytes), metadata(1000));

        assertEquals(2, drafts.size());
        RagChunkDraft metrics = drafts.get(0);
        assertEquals(RagChunkDraft.Boundary.EXACT, metrics.boundary());
        assertTrue(metrics.text().contains("| metric | column_2 |"));
        assertTrue(metrics.text().contains("| latency | 3 |"));
        assertEquals("excel-sheet-rows", metrics.metadata().get("chunk_strategy"));
        assertEquals(true, metrics.metadata().get("structured_rows"));
        assertEquals("poi-workbook", metrics.metadata().get("table_parse"));
        assertEquals(0, metrics.metadata().get("sheet_index"));
        assertEquals("metrics", metrics.metadata().get("sheet_name"));
        assertEquals("metric,column_2", metrics.metadata().get("columns"));
        assertEquals(3, metrics.metadata().get("row_start"));
        assertEquals(3, metrics.metadata().get("row_end"));

        RagChunkDraft headerOnly = drafts.get(1);
        assertEquals(1, headerOnly.metadata().get("sheet_index"));
        assertEquals("header_only", headerOnly.metadata().get("sheet_name"));
        assertEquals(5, headerOnly.metadata().get("row_start"));
        assertEquals(5, headerOnly.metadata().get("row_end"));
        assertEquals("| status |\n| --- |", headerOnly.text().trim());
    }

    @Test
    void shouldIgnoreEmptyWorkbookSheets() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("empty");
            Sheet whitespace = workbook.createSheet("whitespace");
            whitespace.createRow(3).createCell(0).setCellValue("   ");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        List<RagChunkDraft> drafts = extractor.extractWorkbook(file("empty.xlsx", bytes), metadata(1000));

        assertTrue(drafts.isEmpty());
    }

    @Test
    void shouldReturnNoDraftForBlankDelimitedInput() {
        assertTrue(extractor.extractDelimited(" \r\n\t \n", metadata(1000), "empty.csv").isEmpty());
    }

    private Map<String, Object> metadata(int maxSegmentChars) {
        return Map.of(
                "knowledge_scope", "PROJECT",
                "project_id", "demo-project",
                "knowledge", "demo-ops",
                "rag_name", "示例运维",
                "source", "errors.csv",
                "max_segment_chars", maxSegmentChars,
                "hard_split_overlap_chars", 0);
    }

    private RagFileResource file(String name, byte[] bytes) {
        return new ByteArrayRagFileResource("files", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static final class ByteArrayRagFileResource implements RagFileResource {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private ByteArrayRagFileResource(String name,
                                         String originalFilename,
                                         String contentType,
                                         byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String originalFilename() {
            return originalFilename;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public byte[] readAllBytes() {
            return bytes.clone();
        }

        @Override
        public ByteArrayInputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
