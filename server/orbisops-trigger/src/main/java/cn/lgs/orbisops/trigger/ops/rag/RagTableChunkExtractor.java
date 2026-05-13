package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Table-format extraction boundary for delimited text and spreadsheet workbooks.
 *
 * The extractor owns CSV/TSV field parsing, Markdown table projection, workbook
 * traversal, header normalization, row grouping and table-specific metadata.
 * It emits framework-neutral chunk drafts and never constructs Spring AI documents.
 */
public final class RagTableChunkExtractor {

    private final RagChunkMaterializer chunkMaterializer;

    public RagTableChunkExtractor(RagChunkMaterializer chunkMaterializer) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
    }

    public List<RagChunkDraft> extractDelimited(String text,
                                                Map<String, Object> baseMetadata,
                                                String fileName) {
        char delimiter = "tsv".equals(extension(fileName)) ? '\t' : ',';
        List<List<String>> rows = normalize(text).lines()
                .filter(this::hasText)
                .map(line -> parseDelimitedLine(line, delimiter))
                .collect(Collectors.toList());
        if (rows.isEmpty()) {
            return List.of();
        }

        List<String> header = rows.get(0);
        List<List<String>> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();
        List<RagChunkDraft> drafts = new ArrayList<>();
        String headerMarkdown = markdownTableHeader(header);
        StringBuilder current = new StringBuilder(headerMarkdown);
        int rowStart = 1;
        int currentRowStart = rowStart;

        for (int i = 0; i < dataRows.size(); i++) {
            String markdownRow = markdownTableRow(dataRows.get(i), header.size());
            if (current.length() + markdownRow.length() > chunkMaterializer.maxSegmentChars(baseMetadata)
                    && current.length() > headerMarkdown.length()) {
                drafts.add(RagChunkDraft.exact(current.toString(), chunkMaterializer.mergeMetadata(baseMetadata,
                        "chunk_strategy", "table-markdown-rows",
                        "columns", String.join(",", header),
                        "row_start", currentRowStart,
                        "row_end", rowStart + i - 1)));
                current.setLength(0);
                current.append(headerMarkdown);
                currentRowStart = rowStart + i;
            }
            current.append(markdownRow);
        }

        if (!current.isEmpty()) {
            drafts.add(RagChunkDraft.exact(current.toString(), chunkMaterializer.mergeMetadata(baseMetadata,
                    "chunk_strategy", "table-markdown-rows",
                    "columns", String.join(",", header),
                    "row_start", currentRowStart,
                    "row_end", dataRows.isEmpty() ? 0 : rowStart + dataRows.size() - 1)));
        }
        return List.copyOf(drafts);
    }

    public List<RagChunkDraft> extractWorkbook(RagFileResource file,
                                               Map<String, Object> baseMetadata) throws IOException {
        List<RagChunkDraft> drafts = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                appendSheetDrafts(drafts, baseMetadata, workbook.getSheetAt(sheetIndex), sheetIndex, formatter, evaluator);
            }
        }
        return List.copyOf(drafts);
    }

    private void appendSheetDrafts(List<RagChunkDraft> drafts,
                                   Map<String, Object> baseMetadata,
                                   Sheet sheet,
                                   int sheetIndex,
                                   DataFormatter formatter,
                                   FormulaEvaluator evaluator) {
        List<ExcelRow> rows = new ArrayList<>();
        int maxColumns = 0;
        for (Row row : sheet) {
            List<String> cells = excelRowCells(row, formatter, evaluator);
            if (cells.stream().noneMatch(this::hasText)) {
                continue;
            }
            rows.add(new ExcelRow(row.getRowNum() + 1, cells));
            maxColumns = Math.max(maxColumns, cells.size());
        }
        if (rows.isEmpty()) {
            return;
        }

        List<String> header = normalizeExcelHeader(rows.get(0).cells(), maxColumns);
        List<ExcelRow> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();
        String headerMarkdown = markdownTableHeader(header);
        StringBuilder current = new StringBuilder(headerMarkdown);
        int currentRowStart = dataRows.isEmpty() ? rows.get(0).rowNumber() : dataRows.get(0).rowNumber();
        int lastRowNumber = rows.get(0).rowNumber();

        for (ExcelRow row : dataRows) {
            String markdownRow = markdownTableRow(row.cells(), header.size());
            if (current.length() + markdownRow.length() > chunkMaterializer.maxSegmentChars(baseMetadata)
                    && current.length() > headerMarkdown.length()) {
                drafts.add(RagChunkDraft.exact(current.toString(), excelMetadata(
                        baseMetadata,
                        sheet,
                        sheetIndex,
                        header,
                        currentRowStart,
                        lastRowNumber)));
                current.setLength(0);
                current.append(headerMarkdown);
                currentRowStart = row.rowNumber();
            }
            current.append(markdownRow);
            lastRowNumber = row.rowNumber();
        }

        if (!current.isEmpty()) {
            drafts.add(RagChunkDraft.exact(current.toString(), excelMetadata(
                    baseMetadata,
                    sheet,
                    sheetIndex,
                    header,
                    currentRowStart,
                    lastRowNumber)));
        }
    }

    private Map<String, Object> excelMetadata(Map<String, Object> baseMetadata,
                                              Sheet sheet,
                                              int sheetIndex,
                                              List<String> header,
                                              int rowStart,
                                              int rowEnd) {
        return chunkMaterializer.mergeMetadata(baseMetadata,
                "chunk_strategy", "excel-sheet-rows",
                "structured_rows", true,
                "table_parse", "poi-workbook",
                "sheet_index", sheetIndex,
                "sheet_name", sheet.getSheetName(),
                "columns", String.join(",", header),
                "row_start", rowStart,
                "row_end", rowEnd);
    }

    private List<String> excelRowCells(Row row,
                                       DataFormatter formatter,
                                       FormulaEvaluator evaluator) {
        int lastCellNum = Math.max(0, row.getLastCellNum());
        List<String> cells = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            cells.add(formatter.formatCellValue(row.getCell(cellIndex), evaluator).trim());
        }
        return cells;
    }

    private List<String> normalizeExcelHeader(List<String> firstRow, int maxColumns) {
        List<String> header = new ArrayList<>(firstRow);
        while (header.size() < maxColumns) {
            header.add("");
        }
        for (int i = 0; i < header.size(); i++) {
            if (!hasText(header.get(i))) {
                header.set(i, "column_" + (i + 1));
            }
        }
        return header;
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == delimiter && !inQuote) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }

    private String markdownTableHeader(List<String> header) {
        String columns = header.stream().map(this::escapeTableCell).collect(Collectors.joining(" | "));
        String separator = header.stream().map(ignored -> "---").collect(Collectors.joining(" | "));
        return "| " + columns + " |\n| " + separator + " |\n";
    }

    private String markdownTableRow(List<String> row, int expectedColumns) {
        List<String> cells = new ArrayList<>(row);
        while (cells.size() < expectedColumns) {
            cells.add("");
        }
        return "| " + cells.stream()
                .limit(expectedColumns)
                .map(this::escapeTableCell)
                .collect(Collectors.joining(" | ")) + " |\n";
    }

    private String escapeTableCell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ").trim();
    }

    private String extension(String fileName) {
        if (!hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private record ExcelRow(int rowNumber, List<String> cells) {
    }
}
