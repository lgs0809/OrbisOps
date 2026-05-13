package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Format routing, dedicated extractor coordination, and outer Tika/visual fallback. */
@Slf4j
final class RagDocumentParseCoordinator {

    private final RagChunkMaterializer chunkMaterializer;
    private final RagPdfChunkExtractor pdfChunkExtractor;
    private final RagTableChunkExtractor tableChunkExtractor;
    private final RagTextMarkupChunkExtractor textMarkupChunkExtractor;
    private final RagCodeChunkExtractor codeChunkExtractor;
    private final RagConversationChunkExtractor conversationChunkExtractor;
    private final RagTikaChunkExtractor tikaChunkExtractor;
    private final RagVisualFallbackCoordinator visualFallbackCoordinator;
    private final RagDocumentProjector projector;
    private final RagTextResourceReader textReader;
    private final RagDocumentParserSettings settings;
    private final RagVisualDocumentAnalyzer visualDocumentAnalyzer;

    RagDocumentParseCoordinator(
            RagBinaryAssetPort binaryAssets,
            RagChunkMaterializer chunkMaterializer,
            RagVisualFallbackPolicy visualFallbackPolicy,
            RagDocumentParserSettings settings,
            RagVisualDocumentAnalyzer visualDocumentAnalyzer) {
        this.chunkMaterializer = chunkMaterializer;
        this.pdfChunkExtractor = new RagPdfChunkExtractor(binaryAssets, chunkMaterializer);
        this.tableChunkExtractor = new RagTableChunkExtractor(chunkMaterializer);
        this.textMarkupChunkExtractor = new RagTextMarkupChunkExtractor(chunkMaterializer);
        this.codeChunkExtractor = new RagCodeChunkExtractor(chunkMaterializer);
        this.conversationChunkExtractor = new RagConversationChunkExtractor(chunkMaterializer);
        this.tikaChunkExtractor = new RagTikaChunkExtractor(
                chunkMaterializer,
                visualFallbackPolicy);
        this.visualFallbackCoordinator = new RagVisualFallbackCoordinator(
                chunkMaterializer,
                visualFallbackPolicy);
        this.projector = new RagDocumentProjector(chunkMaterializer);
        this.textReader = new RagTextResourceReader();
        this.settings = settings;
        this.visualDocumentAnalyzer = visualDocumentAnalyzer;
    }

    List<Document> parse(
            RagDocumentKind kind,
            RagFileResource file,
            String fileName,
            Map<String, Object> baseMetadata) {
        try {
            return switch (kind) {
                case MARKDOWN -> projector.materialize(
                        textMarkupChunkExtractor.extractMarkdown(
                                textReader.read(file),
                                baseMetadata,
                                "markdown-heading"));
                case HTML -> projector.materialize(
                        textMarkupChunkExtractor.extractHtml(
                                textReader.read(file),
                                baseMetadata));
                case PDF -> parsePdf(file, baseMetadata);
                case TABLE_TEXT -> parseDelimitedTable(
                        textReader.read(file),
                        baseMetadata,
                        fileName);
                case TABLE_BINARY -> parseExcelTable(file, baseMetadata);
                case CODE -> projector.materialize(
                        codeChunkExtractor.extract(textReader.read(file), baseMetadata));
                case CONVERSATION -> parseConversation(
                        textReader.read(file),
                        baseMetadata);
                case IMAGE -> projector.visual(visualFallbackCoordinator.image(
                        file,
                        baseMetadata,
                        visualDocumentAnalyzer));
                case TEXT -> parseText(textReader.read(file), baseMetadata);
                case TIKA -> projector.materialize(tikaChunkExtractor.extract(
                        file,
                        baseMetadata,
                        "tika-generic",
                        false));
            };
        } catch (Exception error) {
            log.warn(
                    "RAG 文档结构化解析失败，降级 Tika 解析 file={} kind={} reason={}",
                    fileName,
                    kind,
                    error.getMessage());
            return projector.materialize(tikaChunkExtractor.extract(
                    file,
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "parse_fallback_reason",
                            error.getMessage()),
                    "tika-fallback",
                    false));
        }
    }

    private List<Document> parseText(
            String text,
            Map<String, Object> baseMetadata) {
        if (conversationChunkExtractor.looksLikeConversation(text)) {
            return parseConversation(
                    text,
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "document_type",
                            "conversation"));
        }
        if (textMarkupChunkExtractor.looksLikeMarkdown(text)) {
            return projector.materialize(textMarkupChunkExtractor.extractMarkdown(
                    text,
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "document_type",
                            "markdown"),
                    "text-markdown-heading"));
        }
        return projector.plainText(
                text,
                chunkMaterializer.mergeMetadata(
                        baseMetadata,
                        "chunk_strategy",
                        "paragraph"));
    }

    private List<Document> parsePdf(
            RagFileResource file,
            Map<String, Object> baseMetadata) {
        RagPdfChunkExtractor.Extraction extraction = pdfChunkExtractor.extract(
                file,
                baseMetadata,
                settings.maxPdfImages());
        if (!extraction.drafts().isEmpty()) {
            return projector.materialize(extraction.drafts());
        }
        return projector.visual(visualFallbackCoordinator.pdfFallback(
                file,
                extraction.metadata(),
                visualDocumentAnalyzer));
    }

    private List<Document> parseDelimitedTable(
            String text,
            Map<String, Object> baseMetadata,
            String fileName) {
        List<RagChunkDraft> drafts = tableChunkExtractor.extractDelimited(
                text,
                baseMetadata,
                fileName);
        if (drafts.isEmpty()) {
            return projector.plainText(
                    text,
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "chunk_strategy",
                            "empty-table-text"));
        }
        return projector.materialize(drafts);
    }

    private List<Document> parseExcelTable(
            RagFileResource file,
            Map<String, Object> baseMetadata) throws IOException {
        List<RagChunkDraft> drafts = tableChunkExtractor.extractWorkbook(
                file,
                baseMetadata);
        if (drafts.isEmpty()) {
            return projector.plainText(
                    "Workbook contains no readable rows.",
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "chunk_strategy",
                            "excel-empty-workbook",
                            "structured_rows",
                            false));
        }
        return projector.materialize(drafts);
    }

    private List<Document> parseConversation(
            String text,
            Map<String, Object> baseMetadata) {
        RagConversationChunkExtractor.Extraction extraction =
                conversationChunkExtractor.extract(text, baseMetadata);
        if (!extraction.structured()) {
            return projector.plainText(
                    text,
                    chunkMaterializer.mergeMetadata(
                            baseMetadata,
                            "chunk_strategy",
                            "conversation-paragraph-fallback"));
        }
        return projector.materialize(extraction.drafts());
    }
}
