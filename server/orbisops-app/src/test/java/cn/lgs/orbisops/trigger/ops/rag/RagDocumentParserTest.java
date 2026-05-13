package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.document.Document;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDocumentParserTest {

    private final TestBinaryAssetPort binaryAssets = new TestBinaryAssetPort();
    private final RagDocumentParser parser = new RagDocumentParser(binaryAssets);

    @Test
    void shouldPreserveMarkdownHeadingsListsAndCodeBlocks() {
        RagFileResource file = file("runbook.md", "text/markdown", """
                # 锁单排障

                - 检查 ERR_LOCK_001
                - 检查 /api/demo-project/join

                ![锁单链路](./images/lock-flow.png)

                ```sql
                select * from order_lock where trace_id = 'abc';
                ```

                ## 日志定位

                按 traceId 和 logger 检索。
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertFalse(documents.isEmpty());
        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("```sql")));
        assertTrue(documents.stream().anyMatch(document -> "锁单排障".equals(document.getMetadata().get("heading_path"))));
        assertTrue(documents.stream().anyMatch(document -> "markdown-image-reference".equals(document.getMetadata().get("chunk_strategy"))
                && "./images/lock-flow.png".equals(document.getMetadata().get("image_path"))));
        assertEquals("markdown", documents.get(0).getMetadata().get("document_type"));
        assertTrue(documents.stream().anyMatch(document -> "markdown-heading".equals(document.getMetadata().get("chunk_strategy"))));
    }

    @Test
    void shouldKeepHtmlCompatibilityAfterMarkupExtractorDelegation() {
        RagFileResource file = file("runbook.html", "text/html", """
                <script>danger()</script>
                <h1>Recovery &amp; Retry</h1>
                <p>Check lock status<br/>Then retry.</p>
                <ul><li>Inspect log</li></ul>
                <pre>select &lt; 3;</pre>
                """);

        List<Document> first = parser.parse("示例运维", "demo-ops", file);
        List<Document> second = parser.parse("示例运维", "demo-ops", file);

        assertFalse(first.isEmpty());
        assertEquals(first.stream().map(Document::getId).toList(), second.stream().map(Document::getId).toList());
        assertEquals(first.stream().map(Document::getText).toList(), second.stream().map(Document::getText).toList());
        assertTrue(first.stream().allMatch(document -> "html".equals(document.getMetadata().get("document_type"))));
        assertTrue(first.stream().allMatch(document -> "html-to-markdown".equals(document.getMetadata().get("chunk_strategy"))));
        String text = first.stream().map(Document::getText).reduce("", (left, right) -> left + "\n" + right);
        assertFalse(text.contains("danger"));
        assertTrue(text.contains("# Recovery & Retry"));
        assertTrue(text.contains("Check lock status"));
        assertTrue(text.contains("- Inspect log"));
        assertTrue(text.contains("select < 3;"));
    }

    @Test
    void shouldKeepTextMarkdownSniffingAfterMarkupExtractorDelegation() {
        RagFileResource file = file("runbook.txt", "text/plain", """
                # Recovery

                Check ERR_LOCK_001.
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("markdown", documents.get(0).getMetadata().get("document_type"));
        assertEquals("text-markdown-heading", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals("Recovery", documents.get(0).getMetadata().get("heading_path"));
        assertEquals(0, documents.get(0).getMetadata().get("section_index"));
        assertEquals(0, documents.get(0).getMetadata().get("chunk_index"));
        assertTrue(documents.get(0).getText().contains("ERR_LOCK_001"));
    }

    @Test
    void shouldOnlyApplyOverlapToOversizedStructuralBlocks() {
        String oversizedParagraph = "X".repeat(2200);
        RagFileResource file = file("runbook.md", "text/markdown", """
                # Lock recovery

                Keep this short paragraph as one semantic unit.

                %s
                """.formatted(oversizedParagraph));

        List<Document> documents = parser.parse(
                "Operations",
                "lock-recovery",
                file,
                new RagParsePolicy("PROJECT", "demo-project", 1000, 100));

        Document semanticParagraph = documents.stream()
                .filter(document -> document.getText().contains("Keep this short paragraph"))
                .findFirst()
                .orElseThrow();
        assertEquals("Lock recovery", semanticParagraph.getMetadata().get("heading_path"));
        assertFalse(Boolean.TRUE.equals(semanticParagraph.getMetadata().get("secondary_split")));

        List<Document> secondaryParts = documents.stream()
                .filter(document -> Boolean.TRUE.equals(document.getMetadata().get("secondary_split")))
                .toList();
        assertTrue(secondaryParts.size() >= 3);
        assertTrue(secondaryParts.stream().allMatch(document ->
                "oversized_structural_block".equals(document.getMetadata().get("split_reason"))
                        && "Lock recovery".equals(document.getMetadata().get("heading_path"))
                        && "PROJECT".equals(document.getMetadata().get("knowledge_scope"))
                        && "demo-project".equals(document.getMetadata().get("project_id"))));

        String first = secondaryParts.get(0).getText();
        String second = secondaryParts.get(1).getText();
        assertEquals(first.substring(first.length() - 100), second.substring(0, 100));

        List<Document> anotherProjectDocuments = parser.parse(
                "Operations",
                "lock-recovery",
                file,
                new RagParsePolicy("PROJECT", "another-project", 1000, 100));
        assertFalse(documents.get(0).getId().equals(anotherProjectDocuments.get(0).getId()));
    }

    @Test
    void shouldParsePdfWithPdfBoxParagraphsImagesAndCaption(@TempDir Path tempDir) throws Exception {
        binaryAssets.setRoot(tempDir);
        RagDocumentParser pdfParser = parser(
                new RagDocumentParserSettings(0, 10),
                null);

        byte[] pdfBytes;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 40, 20);
            graphics.setColor(Color.RED);
            graphics.fillRect(4, 4, 32, 12);
            graphics.dispose();
            PDImageXObject pdfImage = LosslessFactory.createFromImage(pdf, image);

            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 720);
                content.showText("Checkout latency increased because query_time grew.");
                content.newLineAtOffset(0, -16);
                content.showText("Rows_examined indicates a missing composite index.");
                content.endText();

                content.drawImage(pdfImage, 50, 600, 120, 60);

                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(50, 580);
                content.showText("Fig. 1: Lock service topology and callback retry queue.");
                content.endText();
            }
            pdf.save(outputStream);
            pdfBytes = outputStream.toByteArray();
        }

        RagFileResource file = new ByteArrayRagFileResource("files", "incident.pdf", "application/pdf", pdfBytes);

        List<Document> documents = pdfParser.parse("示例运维", "demo-ops", file);

        assertTrue(documents.stream().anyMatch(document -> "pdfbox-paragraph".equals(document.getMetadata().get("chunk_strategy"))
                && document.getText().contains("query_time")));
        Document imageDocument = documents.stream()
                .filter(document -> "pdfbox-image-caption".equals(document.getMetadata().get("chunk_strategy")))
                .findFirst()
                .orElseThrow();
        assertTrue(String.valueOf(imageDocument.getMetadata().get("caption")).contains("Fig. 1"));
        assertTrue(Files.isRegularFile(Path.of(String.valueOf(imageDocument.getMetadata().get("image_path")))));
        assertEquals("image_figure", imageDocument.getMetadata().get("chunk_type"));
    }

    @Test
    void shouldKeepBlankPdfFallbackAfterExtractorDelegation() throws Exception {
        byte[] pdfBytes;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(outputStream);
            pdfBytes = outputStream.toByteArray();
        }
        RagFileResource file = new ByteArrayRagFileResource("files", "incident.pdf", "application/pdf", pdfBytes);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("PDF contains no extractable text or embedded image with the current parser.", documents.get(0).getText());
        assertEquals("pdfbox", documents.get(0).getMetadata().get("parser_engine"));
        assertEquals("page_and_paragraph", documents.get(0).getMetadata().get("evidence_boundary"));
        assertEquals(true, documents.get(0).getMetadata().get("visual_parse_recommended"));
        assertEquals("pdfbox_no_extractable_text_or_image", documents.get(0).getMetadata().get("visual_parse_reason"));
        assertEquals("recommended_manual_enable", documents.get(0).getMetadata().get("visual_parse_status"));
        assertEquals(0, documents.get(0).getMetadata().get("chunk_index"));
    }

    @Test
    void shouldConvertCsvToMarkdownTableRows() {
        RagFileResource file = file("errors.csv", "text/csv", """
                traceId,errorCode,action
                abc123,ERR_LOCK_001,check lock log
                def456,ERR_PAY_002,check payment callback
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertTrue(documents.get(0).getText().contains("| traceId | errorCode | action |"));
        assertTrue(documents.get(0).getText().contains("| abc123 | ERR_LOCK_001 | check lock log |"));
        assertEquals("table", documents.get(0).getMetadata().get("document_type"));
        assertEquals(1, documents.get(0).getMetadata().get("row_start"));
        assertEquals(2, documents.get(0).getMetadata().get("row_end"));
    }

    @Test
    void shouldConvertExcelSheetsToMarkdownTablesWithRowMetadata() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("error_codes");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("errorCode");
            header.createCell(1).setCellValue("action");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("ERR_LOCK_001");
            first.createCell(1).setCellValue("check lock log");
            workbook.write(outputStream);
            workbookBytes = outputStream.toByteArray();
        }

        RagFileResource file = new ByteArrayRagFileResource("files", "errors.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertTrue(documents.get(0).getText().contains("| errorCode | action |"));
        assertTrue(documents.get(0).getText().contains("| ERR_LOCK_001 | check lock log |"));
        assertEquals("excel-sheet-rows", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals("error_codes", documents.get(0).getMetadata().get("sheet_name"));
        assertEquals(2, documents.get(0).getMetadata().get("row_start"));
        assertEquals(2, documents.get(0).getMetadata().get("row_end"));
    }

    @Test
    void shouldKeepQuotedCsvBehaviorAfterTableExtractorDelegation() {
        RagFileResource file = file("quoted.csv", "text/csv", """
                traceId,description,owner
                "abc,123","lock ""retry"" | queue",ops,ignored
                """);

        List<Document> first = parser.parse("示例运维", "demo-ops", file);
        List<Document> second = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, first.size());
        assertEquals(first.get(0).getId(), second.get(0).getId());
        assertEquals(first.get(0).getText(), second.get(0).getText());
        assertTrue(first.get(0).getText().contains("| abc,123 | lock \"retry\" \\| queue | ops |"));
        assertFalse(first.get(0).getText().contains("ignored"));
        assertEquals("traceId,description,owner", first.get(0).getMetadata().get("columns"));
        assertEquals(1, first.get(0).getMetadata().get("row_start"));
        assertEquals(1, first.get(0).getMetadata().get("row_end"));
        assertEquals(0, first.get(0).getMetadata().get("chunk_index"));
    }

    @Test
    void shouldKeepEmptyWorkbookFallbackAfterTableExtractorDelegation() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.createSheet("empty");
            workbook.write(outputStream);
            workbookBytes = outputStream.toByteArray();
        }
        RagFileResource file = new ByteArrayRagFileResource("files", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("Workbook contains no readable rows.", documents.get(0).getText());
        assertEquals("excel-empty-workbook", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals(false, documents.get(0).getMetadata().get("structured_rows"));
        assertEquals("table", documents.get(0).getMetadata().get("document_type"));
        assertEquals(0, documents.get(0).getMetadata().get("chunk_index"));
    }

    @Test
    void shouldSplitCodeByPackageAndSymbolBoundaries() {
        RagFileResource file = file("LockService.java", "text/x-java-source", """
                package cn.example.ops;

                public class LockService {
                    /**
                     * lock order
                     */
                    public void lockOrder(String traceId) {
                        System.out.println(traceId);
                    }
                }
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertFalse(documents.isEmpty());
        assertEquals("code", documents.get(0).getMetadata().get("document_type"));
        assertEquals("code-symbol", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals("cn.example.ops", documents.get(0).getMetadata().get("package"));
        assertTrue(documents.stream().anyMatch(document -> document.getText().contains("lockOrder")));
    }

    @Test
    void shouldSplitConversationByTurnsAndKeepRoles() {
        RagFileResource file = file("incident-chat.txt", "text/plain", """
                [2026-05-08 10:00] 用户: 支付接口变慢
                [2026-05-08 10:01] 运维: 先看 Prometheus p95
                [2026-05-08 10:02] 开发: 同时查 ERROR 日志
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("conversation", documents.get(0).getMetadata().get("document_type"));
        assertEquals("conversation-turns", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals(1, documents.get(0).getMetadata().get("turn_start"));
        assertEquals(3, documents.get(0).getMetadata().get("turn_end"));
        assertTrue(String.valueOf(documents.get(0).getMetadata().get("roles")).contains("运维"));
    }

    @Test
    void shouldKeepSingleTurnConversationFallbackAfterExtractorDelegation() {
        RagFileResource file = file("incident-chat.txt", "text/plain", """
                用户: 只有一个问题
                补充说明仍属于同一个 turn。
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("conversation", documents.get(0).getMetadata().get("document_type"));
        assertEquals("conversation-paragraph-fallback", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals(0, documents.get(0).getMetadata().get("chunk_index"));
        assertTrue(documents.get(0).getText().contains("补充说明"));
        assertFalse(documents.get(0).getMetadata().containsKey("turn_start"));
    }

    @Test
    void shouldKeepTextConversationSniffingBeforeMarkdownSniffing() {
        RagFileResource file = file("notes.txt", "text/plain", """
                用户: # 这不是 Markdown heading
                运维: ``` 这也应先按会话处理
                """);

        List<Document> documents = parser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("conversation", documents.get(0).getMetadata().get("document_type"));
        assertEquals("conversation-turns", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals(1, documents.get(0).getMetadata().get("turn_start"));
        assertEquals(2, documents.get(0).getMetadata().get("turn_end"));
        assertEquals("用户,运维", documents.get(0).getMetadata().get("roles"));
    }

    @Test
    void shouldKeepImageAsPlaceholderWhenVisualAnalyzerDisabled() {
        RagFileResource file = new ByteArrayRagFileResource("files", "incident.png", "image/png", new byte[]{1, 2, 3});

        List<Document> documents = parser.parse("示例故障复盘", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("image", documents.get(0).getMetadata().get("document_type"));
        assertEquals("image-placeholder", documents.get(0).getMetadata().get("chunk_strategy"));
        assertEquals("recommended_manual_enable", documents.get(0).getMetadata().get("visual_parse_status"));
    }

    @Test
    void shouldPassThroughVisualAnalyzerDocumentsAfterCoordinatorDelegation() {
        Document visual = new Document("visual-image", "visual evidence", Map.of("visual", true));
        RagVisualDocumentAnalyzer analyzer = new RagVisualDocumentAnalyzer() {
            @Override
            public boolean shouldAnalyze(Map<String, Object> metadata) {
                return true;
            }

            @Override
            public List<Document> analyzeImage(RagFileResource file, Map<String, Object> baseMetadata) {
                return List.of(visual);
            }
        };
        RagDocumentParser visualParser = parser(
                RagDocumentParserSettings.legacyConstructorDefaults(),
                analyzer);
        RagFileResource file = new ByteArrayRagFileResource("files", "incident.png", "image/png", new byte[]{1, 2, 3});

        List<Document> documents = visualParser.parse("示例运维", "demo-ops", file);

        assertEquals(1, documents.size());
        assertEquals("visual-image", documents.get(0).getId());
        assertEquals("visual evidence", documents.get(0).getText());
        assertEquals(true, documents.get(0).getMetadata().get("visual"));
    }

    @Test
    void shouldGateVisualAnalyzerByEnabledApiKeyAndHighValue() {
        RagVisualDocumentAnalyzer analyzer = new RagVisualDocumentAnalyzer(
                visualSettings(true, true, "openai", "https://api.openai.com", "test-api-key"),
                (cn.lgs.orbisops.application.model.ModelAvailabilityPort) null);

        assertTrue(analyzer.shouldAnalyze(Map.of("high_value_candidate", true)));
        assertFalse(analyzer.shouldAnalyze(Map.of("high_value_candidate", false)));

        RagVisualDocumentAnalyzer disabled = new RagVisualDocumentAnalyzer(
                visualSettings(false, true, "openai", "https://api.openai.com", "test-api-key"),
                (cn.lgs.orbisops.application.model.ModelAvailabilityPort) null);
        assertFalse(disabled.shouldAnalyze(Map.of("high_value_candidate", true)));
    }

    @Test
    void shouldBuildOpenAiCompatibleVisualRequestWithMaxTokens() {
        RagVisualAnalysisSettings settings = new RagVisualAnalysisSettings(
                true, false, "openai", "https://api.example.com", "test-api-key",
                "v1/chat/completions", "llava:7b", "low", 30, 800,
                "max_tokens", "json_object", 1, 3, 4_194_304L, 144);
        RagVisualAnalysisProtocol protocol = new RagVisualAnalysisProtocol(settings);

        JSONObject body = protocol.requestBody(new byte[]{1, 2, 3}, "image/png");

        assertEquals("llava:7b", body.getString("model"));
        assertEquals(800, body.getIntValue("max_tokens"));
        assertFalse(body.containsKey("max_completion_tokens"));
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
    }

    @Test
    void shouldParseFencedVisualJsonAndFillDefaults() {
        RagVisualDocumentProjector projector = new RagVisualDocumentProjector(
                RagVisualAnalysisSettings.defaults());

        JSONObject parsed = projector.parseVisualContent("""
                ```json
                {"title":"支付截图","summary":"展示支付回调失败","confidence":0.82}
                ```
                """);

        assertEquals("支付截图", parsed.getString("title"));
        assertEquals("展示支付回调失败", parsed.getString("summary"));
        assertEquals("", parsed.getString("ocr_text"));
        assertTrue(parsed.getJSONArray("tables").isEmpty());
        assertEquals(0.82D, parsed.getDoubleValue("confidence"), 0.001D);
    }

    @Test
    void shouldProtectParserChunkMaterializationCompatibility() {
        String firstParagraph = "A".repeat(600);
        String secondParagraph = "B".repeat(500);
        RagFileResource file = file("runbook.txt", "text/plain", firstParagraph + "\r\n\r\n" + secondParagraph);
        RagParsePolicy policy = new RagParsePolicy("PROJECT", "demo-project", 1000, 100);

        List<Document> first = parser.parse("Operations", "lock-recovery", file, policy);
        List<Document> second = parser.parse("Operations", "lock-recovery", file, policy);

        assertEquals(2, first.size());
        assertEquals(first.stream().map(Document::getId).toList(), second.stream().map(Document::getId).toList());
        assertEquals(first.stream().map(Document::getText).toList(), second.stream().map(Document::getText).toList());
        assertEquals(first.stream().map(Document::getMetadata).toList(), second.stream().map(Document::getMetadata).toList());
        assertEquals(firstParagraph, first.get(0).getText());
        assertEquals(secondParagraph, first.get(1).getText());
        assertEquals(List.of(0, 1), first.stream()
                .map(document -> (Integer) document.getMetadata().get("chunk_index"))
                .toList());
        assertEquals("PROJECT", first.get(0).getMetadata().get("knowledge_scope"));
        assertEquals("demo-project", first.get(0).getMetadata().get("project_id"));
        assertEquals(1, first.get(0).getMetadata().get("chunk_part"));
        assertEquals(2, first.get(1).getMetadata().get("chunk_part"));

        String seed = "PROJECT:demo-project:lock-recovery:Operations:runbook.txt:0:" + firstParagraph.hashCode();
        assertEquals(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(), first.get(0).getId());
    }

    private RagDocumentParser parser(
            RagDocumentParserSettings settings,
            RagVisualDocumentAnalyzer analyzer) {
        return new RagDocumentParser(
                binaryAssets,
                new RagChunkMaterializer(),
                settings,
                analyzer);
    }

    private RagFileResource file(String name, String contentType, String content) {
        return new ByteArrayRagFileResource("files", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private RagVisualAnalysisSettings visualSettings(
            boolean enabled,
            boolean highValueOnly,
            String provider,
            String baseUrl,
            String apiKey) {
        return new RagVisualAnalysisSettings(
                enabled,
                highValueOnly,
                provider,
                baseUrl,
                apiKey,
                "v1/chat/completions",
                "gpt-5.4-mini",
                "low",
                30,
                1200,
                "max_completion_tokens",
                "json_schema",
                1,
                3,
                4_194_304L,
                144);
    }

    private static final class TestBinaryAssetPort implements RagBinaryAssetPort {
        private Path root;

        private void setRoot(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        public Path store(String knowledge, String source, String fileName, byte[] content) throws IOException {
            if (root == null) throw new IOException("TEST_BINARY_ASSET_ROOT_REQUIRED");
            Path directory = root.resolve(knowledge).resolve(source).normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(fileName).normalize();
            Files.write(target, content);
            return target;
        }

        @Override
        public boolean isRegularFile(Path path) {
            return path != null && Files.isRegularFile(path);
        }

        @Override
        public byte[] read(Path path) throws IOException {
            return Files.readAllBytes(path);
        }
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
