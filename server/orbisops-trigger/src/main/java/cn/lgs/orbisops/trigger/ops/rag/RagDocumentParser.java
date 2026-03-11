package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Structured parser facade: request normalization, metadata, and route delegation. */
@Service
public class RagDocumentParser {

    private final RagDocumentParserSettings settings;
    private final RagDocumentKindDetector kindDetector;
    private final RagDocumentMetadataFactory metadataFactory;
    private final RagDocumentParseCoordinator parseCoordinator;

    public RagDocumentParser(RagBinaryAssetPort binaryAssets) {
        this(
                binaryAssets,
                new RagChunkMaterializer(),
                RagDocumentParserSettings.legacyConstructorDefaults(),
                null);
    }

    RagDocumentParser(
            RagBinaryAssetPort binaryAssets,
            RagChunkMaterializer chunkMaterializer) {
        this(
                binaryAssets,
                chunkMaterializer,
                RagDocumentParserSettings.legacyConstructorDefaults(),
                null);
    }

    @Autowired
    public RagDocumentParser(
            RagBinaryAssetPort binaryAssets,
            RagDocumentParserSettings settings,
            ObjectProvider<RagVisualDocumentAnalyzer> visualAnalyzerProvider) {
        this(
                binaryAssets,
                new RagChunkMaterializer(),
                settings,
                visualAnalyzerProvider == null ? null : visualAnalyzerProvider.getIfAvailable());
    }

    RagDocumentParser(
            RagBinaryAssetPort binaryAssets,
            RagChunkMaterializer chunkMaterializer,
            RagDocumentParserSettings settings,
            RagVisualDocumentAnalyzer visualDocumentAnalyzer) {
        if (binaryAssets == null) {
            throw new IllegalArgumentException("RAG_BINARY_ASSET_PORT_REQUIRED");
        }
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.settings = settings == null ? RagDocumentParserSettings.defaults() : settings;
        this.kindDetector = new RagDocumentKindDetector();
        RagVisualFallbackPolicy visualFallbackPolicy = new RagVisualFallbackPolicy();
        this.metadataFactory = new RagDocumentMetadataFactory(visualFallbackPolicy);
        this.parseCoordinator = new RagDocumentParseCoordinator(
                binaryAssets,
                chunkMaterializer,
                visualFallbackPolicy,
                this.settings,
                visualDocumentAnalyzer);
    }

    public List<Document> parse(
            String ragName,
            String knowledgeTag,
            RagFileResource file) {
        return parse(ragName, knowledgeTag, file, RagParsePolicy.defaults());
    }

    public List<Document> parse(
            String ragName,
            String knowledgeTag,
            RagFileResource file,
            RagParsePolicy parsePolicy) {
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : file.getName();
        RagDocumentKind kind = kindDetector.detect(fileName, file.getContentType());
        RagParsePolicy effectivePolicy = (parsePolicy == null
                ? RagParsePolicy.defaults()
                : parsePolicy).normalized(settings.maxChunkChars());
        Map<String, Object> baseMetadata = metadataFactory.create(
                ragName,
                knowledgeTag,
                file,
                fileName,
                kind,
                effectivePolicy);
        return parseCoordinator.parse(kind, file, fileName, baseMetadata);
    }
}
