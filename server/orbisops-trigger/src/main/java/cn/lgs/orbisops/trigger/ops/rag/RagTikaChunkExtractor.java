package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring AI Tika protocol boundary that translates generic document extraction
 * into framework-neutral chunk drafts.
 */
public final class RagTikaChunkExtractor {

    private static final String EMPTY_TEXT = "Document contains no extractable text with the current parser. OCR or multimodal extraction should be enabled only for high-value documents.";

    private final RagChunkMaterializer chunkMaterializer;
    private final RagVisualFallbackPolicy visualFallbackPolicy;
    private final Function<Resource, List<Document>> reader;

    public RagTikaChunkExtractor(RagChunkMaterializer chunkMaterializer,
                                 RagVisualFallbackPolicy visualFallbackPolicy) {
        this(chunkMaterializer, visualFallbackPolicy, resource -> new TikaDocumentReader(resource).get());
    }

    RagTikaChunkExtractor(RagChunkMaterializer chunkMaterializer,
                          RagVisualFallbackPolicy visualFallbackPolicy,
                          Function<Resource, List<Document>> reader) {
        if (chunkMaterializer == null) throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        if (visualFallbackPolicy == null) throw new IllegalArgumentException("RAG_VISUAL_FALLBACK_POLICY_REQUIRED");
        if (reader == null) throw new IllegalArgumentException("RAG_TIKA_READER_REQUIRED");
        this.chunkMaterializer = chunkMaterializer;
        this.visualFallbackPolicy = visualFallbackPolicy;
        this.reader = reader;
    }

    public List<RagChunkDraft> extract(RagFileResource file,
                                       Map<String, Object> baseMetadata,
                                       String strategy,
                                       boolean tableLike) {
        try {
            String text = reader.apply(resource(file)).stream()
                    .map(Document::getText)
                    .filter(this::hasText)
                    .collect(Collectors.joining("\n\n"));
            Map<String, Object> metadata = chunkMaterializer.mergeMetadata(baseMetadata,
                    "chunk_strategy", strategy,
                    "structured_rows", !tableLike ? "not_applicable" : "false",
                    "table_parse", tableLike ? "tika_text_fallback" : "not_applicable");
            if (!hasText(text)) {
                metadata.put("visual_parse_recommended", true);
                metadata.put("visual_parse_status", visualFallbackPolicy.status(metadata));
                text = EMPTY_TEXT;
            }
            return List.of(RagChunkDraft.structureBounded(text, metadata));
        } catch (Exception e) {
            Map<String, Object> metadata = chunkMaterializer.mergeMetadata(baseMetadata,
                    "chunk_strategy", strategy,
                    "parse_status", "failed",
                    "parse_error", e.getMessage());
            return List.of(RagChunkDraft.exact("Document parsing failed: " + e.getMessage(), metadata));
        }
    }

    private Resource resource(RagFileResource file) {
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "RAG file resource " + file.fileName();
            }

            @Override
            public String getFilename() {
                return file.fileName();
            }

            @Override
            public long contentLength() {
                return file.size();
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return file.openStream();
            }
        };
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
