package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.application.rag.RagBinaryAssetPort;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class RagMultimodalEmbeddingService {

    private final RagBinaryAssetPort binaryAssets;
    private final RagMultimodalSettings settings;
    private final RagMultimodalAvailability availability;
    private final RagMultimodalTableReadiness tableReadiness;
    private final RagMultimodalEmbeddingProtocol embeddingProtocol;
    private final RagMultimodalMediaPreparer mediaPreparer;
    private final RagMultimodalIngestionProjector ingestionProjector;
    private final RagMultimodalVectorStore vectorStore;
    private final RagMultimodalRetrievalCoordinator retrievalCoordinator;

    @Autowired
    public RagMultimodalEmbeddingService(
            RagBinaryAssetPort binaryAssets,
            RagMultimodalRuntimeComponents components) {
        if (binaryAssets == null) throw new IllegalArgumentException("RAG_BINARY_ASSET_PORT_REQUIRED");
        if (components == null) throw new IllegalArgumentException("RAG_MULTIMODAL_RUNTIME_COMPONENTS_REQUIRED");
        this.binaryAssets = binaryAssets;
        this.settings = components.settings();
        this.embeddingProtocol = components.embeddingProtocol();
        this.mediaPreparer = components.mediaPreparer();
        this.ingestionProjector = components.ingestionProjector();
        this.vectorStore = components.vectorStore();
        this.retrievalCoordinator = components.retrievalCoordinator();
        this.availability = components.availability();
        this.tableReadiness = components.tableReadiness();
    }

    @PostConstruct
    public void init() {
        if (isConfigured() && settings.autoInit()) {
            tableReadiness.ensureReady();
        }
    }

    public boolean isSearchAvailable() {
        return isConfigured() && tableReadiness.ensureReady();
    }

    public void storeDocuments(List<Document> documents, RagFileResource file) {
        if (!isConfigured() || documents == null || documents.isEmpty()) {
            return;
        }
        if (!tableReadiness.ensureReady()) {
            return;
        }
        if (settings.indexTextDocuments()) {
            storeTextDocuments(documents);
        }
        if (settings.indexOriginalMedia() && file != null) {
            storeOriginalMedia(documents, file);
        }
        storeLinkedImages(documents);
    }

    public List<Document> search(String query, String filterExpression, int topK) {
        if (!StringUtils.hasText(query) || !isSearchAvailable()) {
            return Collections.emptyList();
        }
        try {
            return retrievalCoordinator.search(query, filterExpression, topK);
        } catch (Exception e) {
            log.warn("多模态向量检索失败，降级为空结果：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isConfigured() {
        RagMultimodalAvailability.Decision decision = availability.evaluate();
        if (decision.warnUnavailable()) {
            log.warn("RAG 多模态索引已开启但配置不完整，provider={} baseUrlConfigured={} apiKeyConfigured={} repositoryConfigured={}",
                    decision.provider(),
                    decision.baseUrlConfigured(),
                    decision.apiKeyConfigured(),
                    decision.repositoryAvailable());
        }
        return decision.available();
    }

    private void storeTextDocuments(List<Document> documents) {
        for (Document document : documents) {
            try {
                RagMultimodalIngestionProjector.TextProjection projection = ingestionProjector
                        .projectText(document)
                        .orElse(null);
                if (projection == null) {
                    continue;
                }
                List<Double> embedding = embeddingProtocol.embedText(projection.content(), "document");
                vectorStore.upsert(projection.id(), projection.content(), projection.metadata(), embedding);
            } catch (Exception e) {
                log.warn("文本 chunk 多模态向量入库失败 source={} reason={}",
                        document == null ? "unknown" : document.getMetadata().get("source"),
                        e.getMessage());
            }
        }
    }

    private void storeOriginalMedia(List<Document> documents, RagFileResource file) {
        String fileName = file.fileName();
        try {
            RagMultimodalMediaPreparer.Preparation preparation = mediaPreparer.prepareOriginal(file);
            logMediaRejections(preparation.rejections(), fileName);
            preparation.media().forEach(media -> storePreparedMedia(documents, media));
        } catch (Exception e) {
            log.warn("原始媒体多模态向量入库失败 file={} reason={}", fileName, e.getMessage());
        }
    }

    private void storeLinkedImages(List<Document> documents) {
        for (Document document : documents) {
            Object imagePathValue = document.getMetadata().get("image_path");
            if (imagePathValue == null) {
                continue;
            }
            Path imagePath = Path.of(String.valueOf(imagePathValue));
            if (!imagePath.isAbsolute()) {
                continue;
            }
            if (!binaryAssets.isRegularFile(imagePath)) {
                log.debug("多模态 linked image 跳过，文件不存在或不在受管目录 path={}", imagePath);
                continue;
            }
            try {
                RagMultimodalMediaPreparer.Preparation preparation = mediaPreparer.prepareImage(
                        binaryAssets.read(imagePath),
                        String.valueOf(document.getMetadata().getOrDefault("image_mime_type", "image/png")),
                        ingestionProjector.pageNumber(document.getMetadata().get("page_number")),
                        String.valueOf(document.getMetadata().getOrDefault("chunk_type", "linked_image")));
                logMediaRejections(preparation.rejections(), imagePath.toString());
                preparation.media().forEach(media -> storePreparedMedia(List.of(document), media));
            } catch (Exception e) {
                log.warn("linked image 多模态向量入库失败 path={} reason={}", imagePath, e.getMessage());
            }
        }
    }

    private void storePreparedMedia(
            List<Document> documents,
            RagMultimodalMediaPreparer.PreparedMedia media) {
        RagMultimodalIngestionProjector.MediaProjection projection =
                ingestionProjector.projectMedia(documents, media);
        try {
            List<Double> embedding = embeddingProtocol.embedImage(
                    projection.content(), projection.bytes(), projection.mimeType(), "document");
            vectorStore.upsert(projection.id(), projection.content(), projection.metadata(), embedding);
        } catch (Exception e) {
            log.warn("图片多模态向量入库失败 source={} page={} reason={}",
                    projection.source(), projection.pageNumber(), e.getMessage());
        }
    }

    private void logMediaRejections(
            List<RagMultimodalMediaPreparer.Rejection> rejections,
            String source) {
        for (RagMultimodalMediaPreparer.Rejection rejection : rejections) {
            switch (rejection.reason()) {
                case EMPTY_CONTENT -> {
                }
                case UNDECODABLE_IMAGE -> log.warn(
                        "图片多模态向量入库跳过，无法解码 source={} mimeType={}",
                        source,
                        rejection.mimeType());
                case UNSUPPORTED_IMAGE_MIME -> log.warn(
                        "图片多模态向量入库跳过，不支持的 mimeType={} source={}",
                        rejection.mimeType(),
                        source);
                case IMAGE_TOO_LARGE -> log.warn(
                        "图片多模态向量入库跳过，超过大小限制 bytes={} max={} source={}",
                        rejection.byteSize(),
                        settings.maxImageBytes(),
                        source);
            }
        }
    }
}
